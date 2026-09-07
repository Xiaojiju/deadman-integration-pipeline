package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 创建设备端点。地址只走 {@code properties}。
 *
 * <pre>{@code
 * POST /catalog/devices/{code}/endpoints
 * {"channelId":"ydlink","properties":[{"attribute":"slaveId","attributeValue":"1","dataType":"int"}]}
 * }</pre>
 */
public record DeviceEndpointWriteRequest(
        @NotBlank(message = "channelId 不能为空")
        String channelId,
        List<PropertyItem> properties
) {
}
