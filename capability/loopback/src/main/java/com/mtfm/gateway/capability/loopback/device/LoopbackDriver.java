package com.mtfm.gateway.capability.loopback.device;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.RawInbound;

/**
 * 南向回环 Driver：将线路文本解码为未贴胶 {@link EnvelopeDraft}。
 *
 * @see LoopbackCodec
 */
public final class LoopbackDriver implements Driver {

    @Override
    public String capabilityType() {
        return LoopbackCapability.TYPE;
    }

    @Override
    public EnvelopeDraft decode(RawInbound raw) throws DecodeException {
        if (raw == null) {
            throw new DecodeException(Failure.decodeError(capabilityType(), "RawInbound 为空"));
        }
        return LoopbackCodec.decode(raw.textOrUtf8(), raw.deviceIdHint());
    }
}
