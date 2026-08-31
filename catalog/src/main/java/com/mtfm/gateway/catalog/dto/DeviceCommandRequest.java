package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 向已登记设备手动下发功能指令。
 *
 * @param functionId 产品功能 ID（须属于该设备产品）
 * @param arguments  本次命令参数；未传字段会与产品 option / 设备覆盖合并
 */
public record DeviceCommandRequest(
        String functionId,
        Map<String, Object> arguments
) {
}
