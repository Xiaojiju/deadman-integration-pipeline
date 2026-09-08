package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 更新设备时附带的端点地址补丁。
 */
public record DeviceEndpointPatchRequest(
        @NotBlank(message = "endpoint id 不能为空")
        String id,
        List<PropertyItem> properties
) {
}
