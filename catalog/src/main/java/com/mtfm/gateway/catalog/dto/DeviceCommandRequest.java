package com.mtfm.gateway.catalog.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 向已 load 的设备下发功能。
 *
 * <pre>{@code
 * POST /catalog/devices/door-1/commands
 * {"functionId":"remoteControlDoor","arguments":{"target":"65535","command":"open"}}
 * }</pre>
 *
 * @param functionId 产品功能 ID
 * @param arguments  本次参数；未传字段与产品默认 / 设备覆盖合并
 * @param requestId  调用方关联号，空则网关生成
 * @param source     来源，如 {@code scheduler}；空表示人工
 */
public record DeviceCommandRequest(
        @NotBlank(message = "functionId 不能为空")
        String functionId,
        Map<String, Object> arguments,
        String requestId,
        String source
) {
}
