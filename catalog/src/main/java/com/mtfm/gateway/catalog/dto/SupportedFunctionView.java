package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;

import java.util.List;
import java.util.Map;

/**
 * /supported 单功能完整表单结构。
 */
public record SupportedFunctionView(
        String functionId,
        String description,
        String accessType,
        int accessPermission,
        String writeAccessType,
        List<FormField> fields,
        List<PropertyItem> properties,
        List<ValueOption> writeValueOptions,
        Map<String, List<ValueOption>> choiceOptions
) {
}
