package com.mtfm.gateway.capability.cloud;

import java.util.Map;

/**
 * 北向 MQTT 入站命令。须经 catalog 装配后再 submit，禁止直接 {@code accept(COMMAND)}。
 *
 * @param requestId  调用方关联号，可空（catalog 会生成）
 * @param deviceId   设备编码
 * @param functionId 产品功能 ID
 * @param arguments  调用方参数
 */
public record NorthboundCommand(
        String requestId,
        String deviceId,
        String functionId,
        Map<String, Object> arguments
) {

    public NorthboundCommand {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
