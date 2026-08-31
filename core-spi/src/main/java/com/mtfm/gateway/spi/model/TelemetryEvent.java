package com.mtfm.gateway.spi.model;

/**
 * 非执行上报体。由 {@link EnvelopeKind#TELEMETRY} 信封物化，跳过 Execute。
 *
 * @param requestId  请求 ID
 * @param deviceId   设备 ID
 * @param functionId 功能 ID，可空
 * @param points     遥测点数据
 */
public record TelemetryEvent(
        String requestId,
        String deviceId,
        String functionId,
        Attributes points
) implements OutboundBody {

    public TelemetryEvent {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        points = points == null ? Attributes.empty() : points;
    }

    @Override
    public EnvelopeKind kind() {
        return EnvelopeKind.TELEMETRY;
    }

    public TelemetryEvent withPoints(Attributes newPoints) {
        return new TelemetryEvent(requestId, deviceId, functionId, newPoints);
    }

    /** 从已贴胶 TELEMETRY 信封物化。 */
    public static TelemetryEvent from(Envelope envelope) {
        if (envelope.kind() != EnvelopeKind.TELEMETRY) {
            throw new IllegalArgumentException("仅 TELEMETRY 可物化为 TelemetryEvent");
        }
        return new TelemetryEvent(
                envelope.requestId(),
                envelope.deviceId(),
                envelope.functionId(),
                envelope.payload()
        );
    }
}
