package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.util.List;
import java.util.Map;

/**
 * 创建设备实例请求。优先 functionOverrides；兼容旧 optionOverrides Map。
 */
public record DeviceWriteRequest(
        String deviceCode,
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Map<String, Object> optionOverrides,
        Boolean enabled
) {
}
