package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 设备对外视图：覆盖按 functionId → PropertyItem[]。
 *
 * @param loaded 是否已绑定到运行时（POST /devices/{code}/load）
 */
public record DeviceView(
        String id,
        String deviceCode,
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        boolean loaded
) {

    public DeviceView withLoaded(boolean loaded) {
        return new DeviceView(
                id, deviceCode, productId, name, functionOverrides, enabled, createdAt, updatedAt, loaded);
    }
}
