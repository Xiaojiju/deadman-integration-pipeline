package com.mtfm.gateway.runtime.schedule;

import com.mtfm.gateway.spi.concurrent.DeadlineDaemon;
import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import com.mtfm.gateway.spi.port.ReplyOccupancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单时间轮定时下发。按设备整体替换任务，不为每设备建 Timer。
 */
public final class ScheduleDispatcher implements DeviceScheduleRegistry, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleDispatcher.class);
    private static final long MAX_JITTER_MS = 500L;

    @FunctionalInterface
    public interface TickHandler {
        void onDue(String deviceId, String functionId);
    }

    private final long minIntervalMs;
    private final ConcurrentHashMap<String, AtomicLong> generations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final DeadlineDaemon queue = new DeadlineDaemon();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile TickHandler handler;
    private volatile ReplyOccupancy occupancy;
    private volatile GatewayMetrics metrics;

    public ScheduleDispatcher() {
        this(MIN_INTERVAL_MS);
    }

    public ScheduleDispatcher(long minIntervalMs) {
        if (minIntervalMs < 1) {
            throw new IllegalArgumentException("minIntervalMs 必须为正");
        }
        this.minIntervalMs = minIntervalMs;
    }

    public void setHandler(TickHandler handler) {
        this.handler = handler;
    }

    public void setOccupancy(ReplyOccupancy occupancy) {
        this.occupancy = occupancy;
    }

    public void setMetrics(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        queue.start("gateway-schedule-dispatcher", this::onDue);
    }

    @Override
    public void replace(String deviceId, List<ScheduledFunction> jobs) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        List<ScheduledFunction> incoming = jobs == null ? List.of() : List.copyOf(jobs);
        synchronized (this) {
            long gen = generations.computeIfAbsent(deviceId, key -> new AtomicLong()).incrementAndGet();
            this.jobs.entrySet().removeIf(entry -> deviceId.equals(entry.getValue().deviceId()));
            Instant now = Instant.now();
            for (ScheduledFunction function : incoming) {
                if (function.intervalMs() < minIntervalMs) {
                    continue;
                }
                String key = key(deviceId, function.functionId());
                this.jobs.put(key, new Job(deviceId, function.functionId(), function.intervalMs(), gen));
                long delay = function.intervalMs() + jitter(function.intervalMs());
                queue.offer(key, gen, now.plusMillis(delay));
            }
        }
    }

    @Override
    public void remove(String deviceId) {
        replace(deviceId, List.of());
    }

    public int size() {
        return jobs.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        jobs.clear();
        generations.clear();
        queue.close();
        started.set(false);
    }

    private void onDue(DeadlineDaemon.Task task) {
        Job job = jobs.get(task.key());
        if (job == null || job.gen() != task.generation()) {
            return;
        }
        queue.offer(task.key(), job.gen(), Instant.now().plusMillis(job.intervalMs()));
        ReplyOccupancy replyOccupancy = occupancy;
        if (replyOccupancy != null && replyOccupancy.awaiting(job.deviceId(), job.functionId())) {
            GatewayMetrics gatewayMetrics = metrics;
            if (gatewayMetrics != null) {
                gatewayMetrics.scheduleSkip(GatewayMetrics.SCHEDULE_SKIP_INFLIGHT);
            }
            return;
        }
        TickHandler tickHandler = handler;
        if (tickHandler == null) {
            return;
        }
        try {
            tickHandler.onDue(job.deviceId(), job.functionId());
        } catch (RuntimeException ex) {
            LOG.warn("定时下发回调失败 deviceId={} functionId={}: {}",
                    job.deviceId(), job.functionId(), ex.getMessage());
        }
    }

    private long jitter(long intervalMs) {
        long bound = Math.min(MAX_JITTER_MS, Math.max(0L, intervalMs / 10L));
        if (bound <= 0L) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(bound + 1);
    }

    static String key(String deviceId, String functionId) {
        return deviceId + "\0" + functionId;
    }

    private record Job(String deviceId, String functionId, long intervalMs, long gen) {
    }
}
