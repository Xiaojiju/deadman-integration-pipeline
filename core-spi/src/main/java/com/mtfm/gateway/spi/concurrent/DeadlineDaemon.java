package com.mtfm.gateway.spi.concurrent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * 共享延迟队列守护线程。功能级 interval、场景 cron、应答超时、出站重试共用同一套 poll/close。
 *
 * <p>使用示例：
 * <pre>{@code
 * DeadlineDaemon daemon = new DeadlineDaemon();
 * daemon.start("reply-timeout", task -> waiter.expire(task.key(), task.generation()));
 * daemon.offer("door-1:req-1", 1L, Instant.now().plusSeconds(2));
 * }</pre>
 */
public final class DeadlineDaemon implements AutoCloseable {

    @FunctionalInterface
    public interface Listener {
        /** 到期回调；实现须自行判断 generation 是否仍有效。 */
        void onDue(Task task);
    }

    /**
     * 延迟项。
     *
     * @param key        业务键，如 {@code deviceId:correlation}
     * @param generation 代数，过期回调时与当前登记比对
     * @param deadline   到期时刻
     */
    public record Task(String key, long generation, Instant deadline) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(Instant.now(), deadline).toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    private final DelayQueue<Task> queue = new DelayQueue<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Listener listener;
    private Thread worker;

    public void start(String threadName, Listener listener) {
        this.listener = listener;
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::drain, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    public void offer(String key, long generation, Instant deadline) {
        if (closed.get() || key == null || deadline == null) {
            return;
        }
        queue.offer(new Task(key, generation, deadline));
    }

    public void clear() {
        queue.clear();
    }

    public boolean removeIf(Predicate<Task> filter) {
        if (filter == null) {
            return false;
        }
        return queue.removeIf(filter);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        queue.clear();
        if (worker != null) {
            worker.interrupt();
        }
        started.set(false);
    }

    private void drain() {
        while (!closed.get()) {
            try {
                Task task = queue.poll(50, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
                Listener current = listener;
                if (current != null) {
                    current.onDue(task);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
                // 单次回调失败不影响后续到期项
            }
        }
    }
}
