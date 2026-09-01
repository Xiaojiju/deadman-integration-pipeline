package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.ValueMapping;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.List;

/**
 * 产品功能视图。
 */
public record ProductFunctionView(
        String id,
        String productId,
        String functionId,
        String description,
        String accessType,
        Integer accessPermission,
        String capabilityType,
        String writeAccessType,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        List<WriteFieldOption> writeFields,
        List<WriteFieldOption> readFields,
        List<ValueOption> readValueOptions,
        Integer sortIndex,
        String publishTopicSlot,
        String subscribeTopicSlot,
        String payloadMode,
        FieldNode structSchema,
        List<ValueMapping> valueMappings) {
}
