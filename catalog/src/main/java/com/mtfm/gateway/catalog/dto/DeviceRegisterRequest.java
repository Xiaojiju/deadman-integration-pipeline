package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 一次性登记设备：创建设备并绑定端点。
 *
 * <pre>{@code
 * POST /catalog/devices/register
 * {"deviceCode":"door-1","productId":"...","load":true,
 *  "endpoints":[{"channelId":"ydlink","properties":[{"attribute":"default_pub","attributeValue":"ydlink/..."}]}]}
 * }</pre>
 */
public record DeviceRegisterRequest(
        @NotBlank(message = "deviceCode 不能为空")
        String deviceCode,
        @NotBlank(message = "productId 不能为空")
        String productId,
        String name,
        Map<String, List<PropertyItem>> functionOverrides,
        Boolean enabled,
        @Valid List<DeviceEndpointWriteRequest> endpoints,
        Boolean load
) {
}
