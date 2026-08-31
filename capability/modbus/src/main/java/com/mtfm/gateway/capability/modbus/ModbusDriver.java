package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.RawInbound;

/**
 * Modbus 入站解码 Driver。
 *
 * <p>南向以 Execute 为主；decode 仅用于测试或透传文本遥测。
 */
public final class ModbusDriver implements Driver {

    @Override
    public String capabilityType() {
        return ModbusCapability.TYPE;
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
                .payload(java.util.Map.of("raw", raw.textOrUtf8()))
                .build();
    }
}
