package com.mtfm.gateway.spi.property;

import com.mtfm.gateway.spi.model.SchemaField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SchemaField ↔ PropertyItem / ValueOption 转换。
 */
public final class PropertySchemas {

    private PropertySchemas() {
    }

    /** schema 字段 → 属性定义（默认值写入 attributeValue）。 */
    public static PropertyItem toPropertyItem(SchemaField field) {
        Object def = field.defaultValue();
        return new PropertyItem(
                field.name(),
                def == null ? "" : String.valueOf(def),
                field.type(),
                field.description());
    }

    public static List<PropertyItem> toPropertyItems(List<SchemaField> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream().map(PropertySchemas::toPropertyItem).toList();
    }

    /** 有 choices 的 schema 字段 → VALUE 写选项（默认项标记 isDefault）。 */
    public static List<ValueOption> choicesToValueOptions(SchemaField field) {
        if (field == null || field.choices() == null || field.choices().isEmpty()) {
            return List.of();
        }
        String defaultText = field.defaultValue() == null ? null : String.valueOf(field.defaultValue());
        List<ValueOption> options = new ArrayList<>();
        for (String choice : field.choices()) {
            boolean isDefault = defaultText != null && defaultText.equals(choice);
            options.add(new ValueOption(choice, choice, choice, field.type(), field.type(), isDefault));
        }
        return List.copyOf(options);
    }

    /** 从多个 schema 字段收集「字段名 → 写选项」；仅含有 choices 的字段。 */
    public static Map<String, List<ValueOption>> choiceOptionsByField(List<SchemaField> fields) {
        Map<String, List<ValueOption>> result = new LinkedHashMap<>();
        if (fields == null) {
            return result;
        }
        for (SchemaField field : fields) {
            List<ValueOption> options = choicesToValueOptions(field);
            if (!options.isEmpty()) {
                result.put(field.name(), options);
            }
        }
        return result;
    }

    /** PropertyItem 列表 → 运行时 Attributes Map。 */
    public static Map<String, Object> toValueMap(List<PropertyItem> items) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (items == null) {
            return values;
        }
        for (PropertyItem item : items) {
            Object typed = item.typedValue();
            if (typed != null) {
                values.put(item.attribute(), typed);
            } else if (item.attributeValue() != null && !item.attributeValue().isBlank()) {
                values.put(item.attribute(), item.attributeValue());
            }
        }
        return values;
    }

    /** Map → PropertyItem 列表（迁移 JSON 袋时用）。 */
    public static List<PropertyItem> fromValueMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<PropertyItem> items = new ArrayList<>();
        values.forEach((key, value) -> items.add(PropertyItem.of(key, value)));
        return List.copyOf(items);
    }
}
