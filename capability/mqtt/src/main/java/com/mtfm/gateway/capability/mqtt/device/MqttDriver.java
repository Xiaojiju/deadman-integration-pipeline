package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.RawInbound;

import java.util.Map;

/**
 * 南向 MQTT 入站 Driver：从 topic/载荷还原子设备 deviceId 与功能草稿。
 */
public final class MqttDriver implements Driver {

    @Override
    public String capabilityType() {
        return MqttCapability.TYPE;
    }

    @Override
    public EnvelopeDraft decode(RawInbound raw) throws DecodeException {
        if (raw == null) {
            throw new DecodeException(Failure.decodeError(capabilityType(), "RawInbound 为空"));
        }
        String deviceId = raw.deviceIdHint();
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = raw.headers().getOrDefault("deviceId", "");
        }
        if (deviceId.isBlank()) {
            throw new DecodeException(Failure.decodeError(capabilityType(), "无法还原子设备 deviceId"));
        }
        EnvelopeKind kind = "COMMAND".equalsIgnoreCase(raw.headers().get("kind"))
                ? EnvelopeKind.COMMAND
                : EnvelopeKind.TELEMETRY;
        String functionId = raw.headers().get("functionId");
        if (kind == EnvelopeKind.COMMAND && (functionId == null || functionId.isBlank())) {
            functionId = MqttCapability.FN_PUBLISH;
        }
        return EnvelopeDraft.builder()
                .kind(kind)
                .deviceId(deviceId)
                .functionId(functionId)
                .capabilityType(capabilityType())
                .payload(Map.of("text", raw.textOrUtf8()))
                .headers(raw.headers())
                .build();
    }
}
