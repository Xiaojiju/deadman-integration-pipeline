package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.util.List;
import java.util.Map;

/**
 * 创建共享通道请求。优先 properties；兼容旧 connection Map。
 */
public record ChannelWriteRequest(
        String code,
        String capabilityType,
        List<PropertyItem> properties,
        Map<String, Object> connection,
        Boolean enabled
) {
}
