package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;

import java.util.List;
import java.util.Map;

/**
 * 产品/设备功能表单视图。
 */
public record FunctionFormView(
        String functionId,
        String description,
        String accessType,
        int accessPermission,
        String capabilityType,
        String writeAccessType,
        List<FormField> parameters,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        Map<String, Object> values,
        Map<String, Object> protocolMapping,
        String payloadMode
) {
}
