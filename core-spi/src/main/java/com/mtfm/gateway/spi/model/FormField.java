package com.mtfm.gateway.spi.model;

import java.util.List;

/**
 * 已绑定当前值的表单字段。可视化按此渲染输入框。
 *
 * <p>使用示例：
 * <pre>{@code
 * FormField field = FormField.from(
 *         SchemaField.required("offset", FieldType.INT, "寄存器偏移"),
 *         100);
 * // field.value() == 100, field.label() == "offset"
 * }</pre>
 *
 * @param name         字段名
 * @param type         固定字段类型
 * @param required     是否必填
 * @param label        显示标签
 * @param description  说明文字
 * @param defaultValue schema 默认值
 * @param value        当前绑定值
 * @param secret       是否敏感字段
 * @param choices      下拉选项
 * @param format       UI 采值约束
 */
public record FormField(
        String name,
        FieldType type,
        boolean required,
        String label,
        String description,
        Object defaultValue,
        Object value,
        boolean secret,
        List<String> choices,
        FieldFormat format
) {

    public FormField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        type = type == null ? FieldType.STRING : type;
        format = format == null ? FieldFormat.NONE : format;
        label = (label == null || label.isBlank()) ? name : label;
        description = description == null ? "" : description;
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /** 从 schema 字段与当前值装配表单字段。 */
    public static FormField from(SchemaField schema, Object value) {
        Object resolved = value != null ? value : schema.defaultValue();
        return new FormField(
                schema.name(),
                schema.type(),
                schema.required(),
                schema.label(),
                schema.description(),
                schema.defaultValue(),
                resolved,
                schema.secret(),
                schema.choices(),
                schema.format()
        );
    }
}
