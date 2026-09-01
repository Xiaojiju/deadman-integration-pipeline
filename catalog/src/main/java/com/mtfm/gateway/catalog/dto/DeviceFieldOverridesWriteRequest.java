package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 设备级字段覆盖写入请求。key 为字段 path（如 deviceId），value 为覆盖值。
 */
public record DeviceFieldOverridesWriteRequest(Map<String, Object> overrides) {
}
