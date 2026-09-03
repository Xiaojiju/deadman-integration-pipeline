package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 北向 MQTT 出站：{@code gw/{deviceId}/response} ← 回执；{@code gw/{deviceId}/telemetry} ← 遥测。
 */
public final class NorthboundMqttPublisher implements NorthboundSink {

    private static final Logger LOG = LoggerFactory.getLogger(NorthboundMqttPublisher.class);

    private final NorthboundMqttSession session;
    private final String responsePattern;
    private final String telemetryPattern;

    public NorthboundMqttPublisher(NorthboundMqttSession session) {
        this(session, NorthboundTopics.DEFAULT_RESPONSE, NorthboundTopics.DEFAULT_TELEMETRY);
    }

    public NorthboundMqttPublisher(
            NorthboundMqttSession session, String responsePattern, String telemetryPattern) {
        this.session = session;
        this.responsePattern = (responsePattern == null || responsePattern.isBlank())
                ? NorthboundTopics.DEFAULT_RESPONSE
                : responsePattern;
        this.telemetryPattern = (telemetryPattern == null || telemetryPattern.isBlank())
                ? NorthboundTopics.DEFAULT_TELEMETRY
                : telemetryPattern;
    }

    @Override
    public void publish(OutboundMessage message) {
        if (session == null || message == null) {
            return;
        }
        String topic;
        if (message.body() instanceof CommandResponse) {
            topic = NorthboundTopics.expand(responsePattern, message.deviceId());
        } else if (message.body() instanceof TelemetryEvent) {
            topic = NorthboundTopics.expand(telemetryPattern, message.deviceId());
        } else {
            return;
        }
        try {
            session.publish(topic, NorthboundJson.stringify(message));
        } catch (RuntimeException ex) {
            LOG.warn("北向 MQTT 出站失败 deviceId={} topic={}: {}",
                    message.deviceId(), topic, ex.getMessage());
        }
    }
}
