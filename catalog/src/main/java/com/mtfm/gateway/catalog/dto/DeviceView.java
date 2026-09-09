package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 设备对外视图：覆盖按 functionId → PropertyItem[]。
 *
 * @param loaded 是否已绑定到运行时（POST /devices/{code}/load）
 * @param online 探针/在线监听状态，空=未知
 */
public record DeviceView(
        String id,
        String deviceCode,
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled,
        Boolean online,
        Instant onlineUpdatedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean loaded
) {

    public DeviceView withLoaded(boolean loaded) {
        return new DeviceView(
                id, deviceCode, productId, name, functionOverrides, enabled,
                online, onlineUpdatedAt, createdAt, updatedAt, loaded);
    }
}
