package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 创建设备实例。覆盖只走 {@code functionOverrides}。
 *
 * <pre>{@code
 * POST /catalog/devices
 * {"deviceCode":"TCL-001","productId":"...","functionOverrides":{"power":[]}}
 * }</pre>
 */
public record DeviceWriteRequest(
        @NotBlank(message = "deviceCode 不能为空")
        String deviceCode,
        @NotBlank(message = "productId 不能为空")
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled
) {
}
