package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.util.List;
import java.util.Map;

/**
 * 创建设备端点请求。优先 properties；兼容旧 address Map。
 */
public record DeviceEndpointWriteRequest(
        String channelId,
        List<PropertyItem> properties,
        Map<String, Object> address
) {
}
