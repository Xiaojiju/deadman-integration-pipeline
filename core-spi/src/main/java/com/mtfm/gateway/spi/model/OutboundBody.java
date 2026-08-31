package com.mtfm.gateway.spi.model;

/**
 * 出站业务体密封接口。种类由实现类型表达。
 *
 * <p>已知实现：{@link CommandResponse}（命令回执）、{@link TelemetryEvent}（遥测上报）。
 */
public sealed interface OutboundBody permits CommandResponse, TelemetryEvent {

    /** 关联设备 ID。 */
    String deviceId();

    /** 关联请求 ID；遥测可空。 */
    default String requestId() {
        return null;
    }

    /** 信封种类。 */
    EnvelopeKind kind();
}
