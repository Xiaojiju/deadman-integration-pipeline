package com.mtfm.gateway.capability.loopback.cloud;

import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 北向回环 Publisher，与南向 loopback.device 分包。
 *
 * <p>默认不注册到流水线，避免与 {@link CloudPublisher} 抢占 CLOUD 通道。
 */
public final class LoopbackCloudPublisher implements Publisher {

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

    public void clear() {
        published.clear();
    }
}
