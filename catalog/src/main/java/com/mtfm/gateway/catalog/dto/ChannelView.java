package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.time.Instant;
import java.util.List;

/**
 * 通道对外视图：属性以 PropertyItem 列表返回。
 */
public record ChannelView(
        String id,
        String code,
        String capabilityType,
        List<PropertyItem> properties,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
