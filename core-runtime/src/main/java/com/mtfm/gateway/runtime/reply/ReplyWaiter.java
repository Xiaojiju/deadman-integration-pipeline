package com.mtfm.gateway.runtime.reply;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 设备应答内存等待器。key = deviceId + 关联号，超时回调出站 TIMEOUT。
 */
public final class ReplyWaiter implements AutoCloseable {

    public record Pending(
            String requestId,
            String deviceId,
            String functionId,
            String correlationValue,
            String resultPath,
            Instant deadline
    ) {
    }

    private final int maxGlobal;
    private final int maxPerDevice;
    private final Consumer<Pending> onTimeout;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> perDevice = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> perFunction = new ConcurrentHashMap<>();
    private final DelayQueue<TimeoutTask> timeouts = new DelayQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Thread worker;

    public ReplyWaiter(int maxGlobal, int maxPerDevice, Consumer<Pending> onTimeout) {
        if (maxGlobal < 1 || maxPerDevice < 1) {
            throw new IllegalArgumentException("应答等待容量必须为正");
        }
        this.maxGlobal = maxGlobal;
        this.maxPerDevice = maxPerDevice;
        this.onTimeout = Objects.requireNonNull(onTimeout, "onTimeout");
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::drainTimeouts, "gateway-reply-waiter");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean tryRegister(Pending item) {
        Objects.requireNonNull(item, "pending");
        if (closed.get() || item.correlationValue() == null || item.correlationValue().isBlank()) {
            return false;
        }
        String key = key(item.deviceId(), item.correlationValue());
        synchronized (this) {
            if (pending.size() >= maxGlobal) {
                return false;
            }
            int deviceCount = perDevice.getOrDefault(item.deviceId(), 0);
            if (deviceCount >= maxPerDevice) {
                return false;
            }
            if (pending.putIfAbsent(key, item) != null) {
                return false;
            }
            perDevice.put(item.deviceId(), deviceCount + 1);
            if (item.functionId() != null && !item.functionId().isBlank()) {
                String functionKey = functionKey(item.deviceId(), item.functionId());
                perFunction.put(functionKey, perFunction.getOrDefault(functionKey, 0) + 1);
            }
        }
        timeouts.offer(new TimeoutTask(key, item.deadline()));
        return true;
    }

    public Optional<Pending> complete(String deviceId, String correlationValue) {
        if (correlationValue == null || correlationValue.isBlank()) {
            return Optional.empty();
        }
        String key = key(deviceId, correlationValue);
        Pending removed = pending.remove(key);
        if (removed == null) {
            return Optional.empty();
        }
        decrementOccupancy(removed);
        return Optional.of(removed);
    }

    /** 同设备同功能是否仍有未完成的应答等待。 */
    public boolean awaiting(String deviceId, String functionId) {
        if (deviceId == null || functionId == null || functionId.isBlank()) {
            return false;
        }
        return perFunction.getOrDefault(functionKey(deviceId, functionId), 0) > 0;
    }

    public void cancel(String deviceId, String correlationValue) {
        complete(deviceId, correlationValue);
    }

    public int size() {
        return pending.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pending.clear();
        perDevice.clear();
        perFunction.clear();
        timeouts.clear();
        if (worker != null) {
            worker.interrupt();
        }
        started.set(false);
    }

    private void drainTimeouts() {
        while (!closed.get()) {
            try {
                TimeoutTask task = timeouts.poll(50, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
                Pending expired = pending.remove(task.key());
                if (expired == null) {
                    continue;
                }
                decrementOccupancy(expired);
                onTimeout.accept(expired);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                // 超时回调失败不影响其它等待项
            }
        }
    }

    private void decrementOccupancy(Pending item) {
        decrementDevice(item.deviceId());
        if (item.functionId() != null && !item.functionId().isBlank()) {
            perFunction.computeIfPresent(functionKey(item.deviceId(), item.functionId()),
                    (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private void decrementDevice(String deviceId) {
        perDevice.computeIfPresent(deviceId, (key, count) -> count <= 1 ? null : count - 1);
    }

    static String key(String deviceId, String correlationValue) {
        return deviceId + "\0" + correlationValue;
    }

    static String functionKey(String deviceId, String functionId) {
        return deviceId + "\0" + functionId;
    }

    private record TimeoutTask(String key, Instant deadline) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(Instant.now(), deadline).toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
