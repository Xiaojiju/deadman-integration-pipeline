package com.mtfm.gateway.runtime.reply;

import com.mtfm.gateway.spi.concurrent.DeadlineDaemon;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 设备应答内存等待器。key = deviceId + 关联号，超时回调出站 TIMEOUT。
 *
 * <p>使用示例：
 * <pre>{@code
 * ReplyWaiter waiter = new ReplyWaiter(4096, 64, pending -> onTimeout(pending));
 * waiter.start();
 * waiter.tryRegister(new ReplyWaiter.Pending("req-1", "door-1", "fn.open", "req-1", deadline));
 * Optional<Pending> hit = waiter.take("door-1", "req-1");
 * }</pre>
 */
public final class ReplyWaiter implements AutoCloseable {

    /**
     * 等待中的命令。
     *
     * @param requestId        上游请求 ID
     * @param deviceId         设备 ID
     * @param functionId       功能 ID
     * @param correlationValue 入站关联号（如 MQTT seq / params.0）
     * @param deadline         超时时刻
     */
    public record Pending(
            String requestId,
            String deviceId,
            String functionId,
            String correlationValue,
            Instant deadline
    ) {
    }

    private final int maxGlobal;
    private final int maxPerDevice;
    private final Consumer<Pending> onTimeout;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> perDevice = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> perFunction = new ConcurrentHashMap<>();
    private final AtomicInteger global = new AtomicInteger();
    private final DeadlineDaemon timeouts = new DeadlineDaemon();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

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
        timeouts.start("gateway-reply-waiter", this::onDue);
    }

    public boolean tryRegister(Pending item) {
        Objects.requireNonNull(item, "pending");
        if (closed.get() || item.correlationValue() == null || item.correlationValue().isBlank()) {
            return false;
        }
        String key = key(item.deviceId(), item.correlationValue());
        if (!reserveGlobal()) {
            return false;
        }
        if (!reserveDevice(item.deviceId())) {
            global.decrementAndGet();
            return false;
        }
        if (pending.putIfAbsent(key, item) != null) {
            releaseDevice(item.deviceId());
            global.decrementAndGet();
            return false;
        }
        if (item.functionId() != null && !item.functionId().isBlank()) {
            perFunction.merge(functionKey(item.deviceId(), item.functionId()), 1, Integer::sum);
        }
        timeouts.offer(key, 0L, item.deadline());
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
        global.set(0);
        timeouts.close();
        started.set(false);
    }

    private void onDue(DeadlineDaemon.Task task) {
        Pending expired = pending.remove(task.key());
        if (expired == null) {
            return;
        }
        decrementOccupancy(expired);
        onTimeout.accept(expired);
    }

    private void decrementOccupancy(Pending item) {
        global.decrementAndGet();
        decrementDevice(item.deviceId());
        if (item.functionId() != null && !item.functionId().isBlank()) {
            perFunction.computeIfPresent(functionKey(item.deviceId(), item.functionId()),
                    (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private boolean reserveGlobal() {
        while (true) {
            int current = global.get();
            if (current >= maxGlobal) {
                return false;
            }
            if (global.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private boolean reserveDevice(String deviceId) {
        boolean[] accepted = {true};
        perDevice.compute(deviceId, (key, count) -> {
            int n = count == null ? 0 : count;
            if (n >= maxPerDevice) {
                accepted[0] = false;
                return count;
            }
            return n + 1;
        });
        return accepted[0];
    }

    private void releaseDevice(String deviceId) {
        decrementDevice(deviceId);
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
}
