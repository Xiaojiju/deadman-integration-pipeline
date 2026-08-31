package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;

import java.util.List;

/**
 * /supported 能力表单投影：字段定义 + 可选枚举选项。
 */
public record SupportedSchemaView(
        List<FormField> fields,
        List<PropertyItem> properties,
        /** 字段名 → 枚举选项（来自 SchemaField.choices）。 */
        java.util.Map<String, List<ValueOption>> choiceOptions
) {
}
