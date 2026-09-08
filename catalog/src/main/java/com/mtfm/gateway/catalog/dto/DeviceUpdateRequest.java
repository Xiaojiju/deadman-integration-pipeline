package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 更新设备。可改名称、编码、启用状态、功能覆盖与端点地址（如序列号）。
 */
public record DeviceUpdateRequest(
        String name,
        String deviceCode,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled,
        @Valid List<DeviceEndpointPatchRequest> endpoints
) {
}
