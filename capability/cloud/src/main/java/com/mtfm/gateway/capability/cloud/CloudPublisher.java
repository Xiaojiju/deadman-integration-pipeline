package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 北向 Hub：channelHint 固定 {@code CLOUD}。先入有界内存快照，再扇出 MQTT / Webhook。
 *
 * <p>扇出失败只打日志，始终返回 success，避免 Egress 重试导致重复投递。
 *
 * <p>使用示例：
 * <pre>{@code
 * CloudPublisher hub = new CloudPublisher(List.of(mqttSink, webhookSink));
 * PublishResult result = hub.publish(outboundMessage);
 * }</pre>
 */
public final class CloudPublisher implements Publisher, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloudPublisher.class);

    static final int SNAPSHOT_LIMIT = 256;

    private final ArrayDeque<OutboundMessage> published = new ArrayDeque<>();
    private final AtomicReference<List<NorthboundSink>> sinks;

    public CloudPublisher() {
        this(List.of());
    }

    public CloudPublisher(NorthboundSink... sinks) {
        this(sinks == null ? List.of() : List.of(sinks));
    }

    public CloudPublisher(List<NorthboundSink> sinks) {
        this.sinks = new AtomicReference<>(sinks == null ? List.of() : List.copyOf(sinks));
    }

    /** 热更换扇出腿；关闭旧的 {@link AutoCloseable} sink，不打断 publish。 */
    public void replaceSinks(List<NorthboundSink> next) {
        List<NorthboundSink> incoming = next == null ? List.of() : List.copyOf(next);
        closeSinks(sinks.getAndSet(incoming));
    }

    @Override
    public String channel() {
        return Channels.CLOUD;
    }

    @Override
    public PublishResult publish(OutboundMessage message) {
        remember(message);
        for (NorthboundSink sink : sinks.get()) {
            try {
                sink.publish(message);
            } catch (RuntimeException ex) {
                LOG.warn("北向扇出失败 {}: {}", sink.getClass().getSimpleName(), ex.getMessage());
            }
        }
        return PublishResult.success();
    }

    public List<OutboundMessage> snapshot() {
        synchronized (published) {
            return new ArrayList<>(published);
        }
    }

    public List<CommandResponse> responses() {
        return snapshot().stream()
                .map(OutboundMessage::body)
                .filter(CommandResponse.class::isInstance)
                .map(CommandResponse.class::cast)
                .toList();
    }

    public List<TelemetryEvent> telemetry() {
        return snapshot().stream()
                .map(OutboundMessage::body)
                .filter(TelemetryEvent.class::isInstance)
                .map(TelemetryEvent.class::cast)
                .toList();
    }

    private void remember(OutboundMessage message) {
        synchronized (published) {
            if (published.size() >= SNAPSHOT_LIMIT) {
                published.removeFirst();
            }
            published.addLast(message);
        }
    }

    @Override
    public void close() {
        closeSinks(sinks.getAndSet(List.of()));
    }

    private static void closeSinks(List<NorthboundSink> closing) {
        if (closing == null || closing.isEmpty()) {
            return;
        }
        for (NorthboundSink sink : closing) {
            if (sink instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ex) {
                    LOG.warn("关闭北向扇出失败 {}: {}", sink.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }
    }
}
