package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.TelemetryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 北向云 Publisher，channelHint 固定为 {@code CLOUD}。
 *
 * <p>接收流水线出站 {@link OutboundMessage}（命令响应 / 遥测事件）并发布到云端。
 * 测试环境可调用 {@link #snapshot()} / {@link #responses()} 断言出站内容。
 */
public final class CloudPublisher implements Publisher {

    private final CopyOnWriteArrayList<OutboundMessage> published = new CopyOnWriteArrayList<>();

    @Override
    public String channel() {
        return Channels.CLOUD;
    }

    @Override
    public PublishResult publish(OutboundMessage message) {
        published.add(message);
        return PublishResult.success();
    }

    public List<OutboundMessage> snapshot() {
        return new ArrayList<>(published);
    }

    public List<CommandResponse> responses() {
        return published.stream()
                .map(OutboundMessage::body)
                .filter(CommandResponse.class::isInstance)
                .map(CommandResponse.class::cast)
                .toList();
    }

    public List<TelemetryEvent> telemetry() {
        return published.stream()
                .map(OutboundMessage::body)
                .filter(TelemetryEvent.class::isInstance)
                .map(TelemetryEvent.class::cast)
                .toList();
    }
}
