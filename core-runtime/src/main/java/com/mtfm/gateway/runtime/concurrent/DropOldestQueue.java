package com.mtfm.gateway.runtime.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 有界队列，满时丢弃最旧元素再放入最新项。
 *
 * <p>用于遥测入站/出站等可丢场景：宁可丢失旧数据，也要保证最新遥测能被处理。
 * {@link #offerDropOldest(Object)} 返回被丢弃的元素列表，便于调用方统计 drop 指标。
 *
 * <p>使用示例：
 * <pre>{@code
 * DropOldestQueue<IngressWork> telemetry = new DropOldestQueue<>(1024);
 * List<IngressWork> dropped = telemetry.offerDropOldest(latestWork);
 * dropped.forEach(w -> metrics.egressDrop("telemetry_ingress"));
 * }</pre>
 */
public final class DropOldestQueue<T> {

    private final ArrayBlockingQueue<T> queue;

    public DropOldestQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public List<T> offerDropOldest(T item) {
        List<T> dropped = new ArrayList<>();
        while (!queue.offer(item)) {
            T old = queue.poll();
            if (old != null) {
                dropped.add(old);
            }
        }
        return dropped;
    }

    public T poll() {
        return queue.poll();
    }

    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }
}
