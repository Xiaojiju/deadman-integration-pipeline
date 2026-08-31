package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.RawInbound;

import java.util.Map;

/**
 * 海康 ISAPI 入站 Driver：将设备事件解码为遥测草稿。
 */
public final class HikvisionDriver implements Driver {

    @Override
    public String capabilityType() {
        return HikvisionCapability.TYPE;
    }

    @Override
    public EnvelopeDraft decode(RawInbound raw) throws DecodeException {
        if (raw == null || raw.deviceIdHint() == null || raw.deviceIdHint().isBlank()) {
            throw new DecodeException(Failure.decodeError(capabilityType(), "deviceIdHint 必填"));
        }
        return EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .deviceId(raw.deviceIdHint())
                .capabilityType(capabilityType())
                .payload(Map.of("event", raw.textOrUtf8()))
                .build();
    }
}
