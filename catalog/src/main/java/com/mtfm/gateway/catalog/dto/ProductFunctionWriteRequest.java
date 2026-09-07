package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 创建/更新产品功能。成败由 {@code readFields} 判定，不再接收 {@code resultPath}。
 *
 * <pre>{@code
 * POST /catalog/products/{id}/functions
 * {"functionId":"light.switch","capabilityType":"MODBUS","accessType":"WRITE",
 *  "writeFields":[{"field":"area","source":"constant","constant":"COIL"}]}
 * }</pre>
 */
public record ProductFunctionWriteRequest(
        @NotBlank(groups = CreateOp.class, message = "functionId 不能为空")
        String functionId,
        String accessType,
        Integer accessPermission,
        @NotBlank(groups = CreateOp.class, message = "capabilityType 不能为空")
        String capabilityType,
        String writeAccessType,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<WriteFieldOption> readFields,
        List<ValueOption> readValueOptions,
        Integer sortIndex,
        String description,
        String publishTopicSlot,
        String subscribeTopicSlot,
        String payloadMode,
        String payloadEncoding,
        String replyTopicSlot,
        String correlationPath,
        String correlationCommandPath,
        Integer replyTimeoutMs,
        Long scheduleIntervalMs,
        Boolean scheduleEnabled,
        String scaleOp,
        String scaleOperand) {
}
