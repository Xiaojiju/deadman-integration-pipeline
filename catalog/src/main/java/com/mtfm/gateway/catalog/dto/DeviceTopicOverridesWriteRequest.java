package com.mtfm.gateway.catalog.dto;

import java.util.Map;

/**
 * 设备级 topic slot 覆盖写入请求。key 为 slot 名（如 default_pub），value 为实际 topic。
 */
public record DeviceTopicOverridesWriteRequest(Map<String, String> overrides) {
}
