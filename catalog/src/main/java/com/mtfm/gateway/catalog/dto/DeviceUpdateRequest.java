package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.util.List;
import java.util.Map;

/**
 * 更新设备（deviceCode / productId 不可改）。
 */
public record DeviceUpdateRequest(
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled
) {
}
