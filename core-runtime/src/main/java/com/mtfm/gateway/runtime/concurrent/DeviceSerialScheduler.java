package com.mtfm.gateway.runtime.concurrent;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备级串行、跨设备并行的任务调度器。
 *
 * <p>同一 {@code deviceId} 的任务严格 FIFO 串行执行，不同设备可并行。
 * 设备队列空闲后回收槽位；活跃槽总数受 {@code maxDeviceSlots} 限制，防止恶意伪造海量 deviceId 耗尽内存。
 *
 * <p>使用示例：
 * <pre>{@code
 * DeviceSerialScheduler scheduler = new DeviceSerialScheduler(4, 64, 4096);
 * boolean enqueued = scheduler.execute("dev-001", () -> engine.execute(command));
 * if (!enqueued) { /* 队列满或槽耗尽 *\/ }
 * scheduler.shutdown();
 * scheduler.awaitTermination(Duration.ofSeconds(5));
 * }</pre>
 */
public final class DeviceSerialScheduler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSerialScheduler.class);

    private final int perDeviceCapacity;
    private final int maxDeviceSlots;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, ArrayBlockingQueue<Runnable>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DeviceSerialScheduler(int workers, int perDeviceCapacity, int maxDeviceSlots) {
        if (workers < 1) {
            throw new IllegalArgumentException("workers 至少为 1");
        }
        if (perDeviceCapacity < 1) {
            throw new IllegalArgumentException("perDeviceCapacity 至少为 1");
        }
        if (maxDeviceSlots < 1) {
            throw new IllegalArgumentException("maxDeviceSlots 至少为 1");
        }
        this.perDeviceCapacity = perDeviceCapacity;
        this.maxDeviceSlots = maxDeviceSlots;
        this.executor = Executors.newFixedThreadPool(workers, namedFactory("gateway-execute"));
    }

    /**
     * @return {@code false} 表示队列满或槽耗尽或已关闭
     */
    public boolean execute(String deviceId, Runnable task) {
        if (closed.get()) {
            return false;
        }
        while (true) {
            if (!queues.containsKey(deviceId) && queues.size() >= maxDeviceSlots) {
                return false;
            }
            ArrayBlockingQueue<Runnable> queue = queues.computeIfAbsent(
                    deviceId, id -> new ArrayBlockingQueue<>(perDeviceCapacity));
            if (!queues.containsKey(deviceId)) {
                return false;
            }
            if (!queue.offer(task)) {
                return false;
            }
            ArrayBlockingQueue<Runnable> current = queues.get(deviceId);
            if (current != queue) {
                if (queue.remove(task)) {
                    continue;
                }
            }
            kick(deviceId);
            return true;
        }
    }

    public int deviceSlots() {
        return queues.size();
    }

    private void kick(String deviceId) {
        if (closed.get()) {
            return;
        }
        if (running.putIfAbsent(deviceId, Boolean.TRUE) != null) {
            return;
        }
        try {
            executor.execute(() -> runDevice(deviceId));
        } catch (RuntimeException ex) {
            running.remove(deviceId);
            LOG.warn("无法调度设备任务 {}: {}", deviceId, ex.getMessage());
        }
    }

    private void runDevice(String deviceId) {
        ArrayBlockingQueue<Runnable> queue = queues.get(deviceId);
        try {
            while (queue != null) {
                Runnable next = queue.poll();
                if (next == null) {
                    break;
                }
                try {
                    next.run();
                } catch (Throwable ex) {
                    LOG.warn("设备 {} 任务未捕获异常: {}", deviceId, String.valueOf(ex.getMessage()));
                }
            }
        } finally {
            running.remove(deviceId);
            queue = queues.get(deviceId);
            if (queue != null && queue.peek() != null) {
                kick(deviceId);
            } else if (queue != null) {
                queues.remove(deviceId, queue);
                if (queue.peek() != null) {
                    ArrayBlockingQueue<Runnable> winner = queues.putIfAbsent(deviceId, queue);
                    if (winner != null && winner != queue) {
                        Runnable leftover;
                        while ((leftover = queue.poll()) != null) {
                            if (!winner.offer(leftover)) {
                                try {
                                    leftover.run();
                                } catch (Throwable ex) {
                                    LOG.warn("转移溢出任务失败: {}", ex.getMessage());
                                }
                            }
                        }
                    }
                    kick(deviceId);
                }
            }
        }
    }

    public void shutdown() {
        closed.set(true);
        executor.shutdown();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        closed.set(true);
        executor.shutdownNow();
    }

    public static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + seq.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}
