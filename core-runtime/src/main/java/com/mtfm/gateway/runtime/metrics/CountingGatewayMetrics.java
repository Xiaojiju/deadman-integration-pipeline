package com.mtfm.gateway.runtime.metrics;

import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.MessagePriority;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 基于 {@link LongAdder} 的内存计数指标实现，未注入外部 {@link GatewayMetrics} 时由核心默认使用。
 *
 * <p>可通过 {@link GatewayPipeline#stats()} 读取快照，例如：
 * {@code pipeline.stats().get("drop.telemetry_ingress")}。
 */
public final class CountingGatewayMetrics implements GatewayMetrics {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    @Override
    public void correlateResponse(ExecutionStatus status) {
        bump("correlate." + status.name());
    }

    @Override
    public void egressQueueDepth(MessagePriority priority, int depth) {
        counters.computeIfAbsent("depth." + priority.name(), key -> new LongAdder()).reset();
        counters.get("depth." + priority.name()).add(depth);
    }

    @Override
    public void egressPublish(String channel, EnvelopeKind kind, String result) {
        bump("publish." + channel + "." + kind + "." + result);
    }

    @Override
    public void egressDrop(String reason) {
        bump("drop." + reason);
    }

    @Override
    public void egressRetry(String channel) {
        bump("retry." + channel);
    }

    @Override
    public void scheduleSkip(String reason) {
        bump("schedule.skip." + reason);
    }

    public long get(String key) {
        LongAdder adder = counters.get(key);
        return adder == null ? 0L : adder.sum();
    }

    private void bump(String key) {
        counters.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }
}
