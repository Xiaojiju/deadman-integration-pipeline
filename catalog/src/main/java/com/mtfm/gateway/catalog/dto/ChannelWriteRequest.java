package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.spi.property.PropertyItem;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 创建/更新共享通道。连接参数只走 {@code properties}，不再接受 JSON {@code connection}。
 *
 * <pre>{@code
 * POST /catalog/channels
 * {"code":"modbus-1","capabilityType":"MODBUS",
 *  "properties":[{"attribute":"host","attributeValue":"10.0.0.8","dataType":"string"}]}
 * }</pre>
 */
public record ChannelWriteRequest(
        @NotBlank(groups = CreateOp.class, message = "通道 code 不能为空")
        String code,
        @NotBlank(groups = CreateOp.class, message = "capabilityType 不能为空")
        String capabilityType,
        List<PropertyItem> properties,
        Boolean enabled
) {
}
