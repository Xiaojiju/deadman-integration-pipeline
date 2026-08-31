package com.mtfm.gateway.spi.model;

import java.util.List;

/**
 * 能力/功能字段说明。底层仍可落 JSON，调用方按本结构渲染表单。
 *
 * <p>使用示例：
 * <pre>{@code
 * SchemaField host = SchemaField.required("host", "string", "Modbus 主机", "127.0.0.1");
 * SchemaField secret = SchemaField.secret("password", "连接密码");
 * SchemaField mode = SchemaField.choice("mode", "工作模式", true, "RTU",
 *         List.of("RTU", "ASCII"));
 * }</pre>
 *
 * @param name         字段名
 * @param type         控件类型（string/int/boolean/select/password 等）
 * @param required     是否必填
 * @param description  说明文字
 * @param label        显示标签，默认同 name
 * @param defaultValue 默认值
 * @param secret       是否敏感字段（密码框）
 * @param choices      下拉选项，type 为 select 时使用
 */
public record SchemaField(
        String name,
        String type,
        boolean required,
        String description,
        String label,
        Object defaultValue,
        boolean secret,
        List<String> choices
) {

    public SchemaField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (type == null || type.isBlank()) {
            type = "string";
        }
        description = description == null ? "" : description;
        label = (label == null || label.isBlank()) ? name : label;
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public SchemaField(String name, String type, boolean required, String description) {
        this(name, type, required, description, name, null, false, List.of());
    }

    /** 创建必填字段。 */
    public static SchemaField required(String name, String type, String description) {
        return new SchemaField(name, type, true, description);
    }

    /** 创建可选字段。 */
    public static SchemaField optional(String name, String type, String description) {
        return new SchemaField(name, type, false, description);
    }

    /** 创建带默认值的必填字段。 */
    public static SchemaField required(String name, String type, String description, Object defaultValue) {
        return new SchemaField(name, type, true, description, name, defaultValue, false, List.of());
    }

    public static SchemaField optional(String name, String type, String description, Object defaultValue) {
        return new SchemaField(name, type, false, description, name, defaultValue, false, List.of());
    }

    /** 创建必填敏感字段（密码框）。 */
    public static SchemaField secret(String name, String description) {
        return new SchemaField(name, "password", true, description, name, null, true, List.of());
    }

    public static SchemaField optionalSecret(String name, String description) {
        return new SchemaField(name, "password", false, description, name, null, true, List.of());
    }

    /** 创建带默认值的必填下拉字段。 */
    public static SchemaField choice(String name, String description, boolean required, Object defaultValue,
            List<String> choices) {
        return new SchemaField(name, "select", required, description, name, defaultValue, false, choices);
    }
}
