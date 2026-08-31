package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.time.Instant;
import java.util.List;

/**
 * 设备端点对外视图：address 属性为 PropertyItem 列表。
 */
public record DeviceEndpointView(
        String id,
        String deviceId,
        String channelId,
        List<PropertyItem> properties,
        Instant createdAt
) {
}
