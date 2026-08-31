package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.util.List;
import java.util.Map;

/**
 * 一次性登记设备：创建设备 + 绑定端点。
 */
public record DeviceRegisterRequest(
        String deviceCode,
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Map<String, Object> optionOverrides,
        Boolean enabled,
        List<DeviceEndpointWriteRequest> endpoints,
        Boolean load
) {
}
