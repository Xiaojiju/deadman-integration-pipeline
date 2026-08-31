package com.mtfm.gateway.capability.loopback.device;

import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.Failure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回环编解码器，文本格式：{@code KIND|deviceId|functionId|k=v,k=v}。
 *
 * <pre>{@code
 * // TELEMETRY|dev-001|fn.status|
 * // COMMAND|dev-001|fn.switch|action=on
 * }</pre>
 */
public final class LoopbackCodec {

    private LoopbackCodec() {
    }

    public static EnvelopeDraft decode(String text, String deviceIdHint) {
        if (text == null || text.isBlank()) {
            throw new DecodeException(Failure.decodeError(LoopbackCapability.TYPE, "空载荷"));
        }
        String[] parts = text.split("\\|", 4);
        if (parts.length < 3) {
            throw new DecodeException(Failure.decodeError(LoopbackCapability.TYPE, "格式须为 KIND|deviceId|functionId|k=v"));
        }
        EnvelopeKind kind;
        try {
            kind = EnvelopeKind.valueOf(parts[0].trim());
        } catch (IllegalArgumentException ex) {
            throw new DecodeException(Failure.decodeError(LoopbackCapability.TYPE, "未知 kind: " + parts[0]), ex);
        }
        String deviceId = parts[1].isBlank() ? deviceIdHint : parts[1].trim();
        if (deviceId == null || deviceId.isBlank()) {
            throw new DecodeException(Failure.decodeError(LoopbackCapability.TYPE, "deviceId 缺失"));
        }
        String functionId = parts[2].isBlank() ? null : parts[2].trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (parts.length == 4) {
            for (String pair : parts[3].split(",")) {
                if (pair.isBlank()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                if (eq < 0) {
                    payload.put(pair.trim(), Boolean.TRUE);
                } else {
                    payload.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return EnvelopeDraft.builder()
                .kind(kind)
                .deviceId(deviceId)
                .functionId(functionId)
                .capabilityType(LoopbackCapability.TYPE)
                .payload(payload)
                .build();
    }
}
