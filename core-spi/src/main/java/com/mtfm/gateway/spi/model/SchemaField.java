package com.mtfm.gateway.spi.model;

import java.util.List;

/**
 * 能力/功能字段说明。底层仍可落 JSON，调用方按本结构渲染表单。
 *
 * <p>
 * 使用示例：
 *
 * <pre>{@code
 * SchemaField host = SchemaField.required("host", FieldType.STRING, "Modbus 主机", "127.0.0.1");
 * SchemaField secret = SchemaField.secret("password", "连接密码");
 * SchemaField port = SchemaField.optional("port", FieldType.INT, "TCP 端口", 502).range(1, 65535);
 * SchemaField begin = SchemaField.optional("beginTime", FieldType.STRING, "生效开始", FieldFormat.DATETIME_ISO8601);
 * SchemaField face = SchemaField.optional("imgStr", FieldType.STRING, "人脸图片", FieldFormat.IMAGE_BASE64);
 * SchemaField mode = SchemaField.choice("mode", "工作模式", true, "RTU", List.of("RTU", "ASCII"));
 * }</pre>
 *
 * @param name         字段名
 * @param type         固定字段类型
 * @param required     是否必填
 * @param description  说明文字
 * @param label        显示标签，默认同 name
 * @param defaultValue 默认值
 * @param secret       是否敏感字段（密码框）
 * @param choices      下拉选项，type 为 SELECT 时使用
 * @param format       采值约束（与 type 解耦）
 * @param minimum      整数下界（含），仅 INT 有意义
 * @param maximum      整数上界（含），仅 INT 有意义
 */
public record SchemaField(
        String name,
        FieldType type,
        boolean required,
        String description,
        String label,
        Object defaultValue,
        boolean secret,
        List<String> choices,
        FieldFormat format,
        Integer minimum,
        Integer maximum) {

    public SchemaField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        type = type == null ? FieldType.STRING : type;
        format = format == null ? FieldFormat.NONE : format;
        description = description == null ? "" : description;
        label = (label == null || label.isBlank()) ? name : label;
        choices = choices == null ? List.of() : List.copyOf(choices);
        if (type == FieldType.PASSWORD) {
            secret = true;
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("minimum 不能大于 maximum");
        }
    }

    public SchemaField(String name, FieldType type, boolean required, String description) {
        this(name, type, required, description, name, null, false, List.of(), FieldFormat.NONE, null, null);
    }

    /** 创建必填字段。 */
    public static SchemaField required(String name, FieldType type, String description) {
        return new SchemaField(name, type, true, description);
    }

    /** 创建可选字段。 */
    public static SchemaField optional(String name, FieldType type, String description) {
        return new SchemaField(name, type, false, description);
    }

    /** 创建带默认值的必填字段。 */
    public static SchemaField required(String name, FieldType type, String description, Object defaultValue) {
        return new SchemaField(name, type, true, description, name, defaultValue, false, List.of(), FieldFormat.NONE,
                null, null);
    }

    /** 创建带默认值的可选字段。 */
    public static SchemaField optional(String name, FieldType type, String description, Object defaultValue) {
        return new SchemaField(name, type, false, description, name, defaultValue, false, List.of(), FieldFormat.NONE,
                null, null);
    }

    /** 创建带约束的可选字段。 */
    public static SchemaField optional(String name, FieldType type, String description, FieldFormat format) {
        return new SchemaField(name, type, false, description, name, null, false, List.of(), format, null, null);
    }

    /** 创建带约束的必填字段。 */
    public static SchemaField required(String name, FieldType type, String description, FieldFormat format) {
        return new SchemaField(name, type, true, description, name, null, false, List.of(), format, null, null);
    }

    /** 创建带默认值与约束的可选字段。 */
    public static SchemaField optional(String name, FieldType type, String description, Object defaultValue,
            FieldFormat format) {
        return new SchemaField(name, type, false, description, name, defaultValue, false, List.of(), format, null,
                null);
    }

    /** 创建带默认值与约束的必填字段。 */
    public static SchemaField required(String name, FieldType type, String description, Object defaultValue,
            FieldFormat format) {
        return new SchemaField(name, type, true, description, name, defaultValue, false, List.of(), format, null, null);
    }

    /** 兼容旧调用：type 为字符串 wire。 */
    public static SchemaField required(String name, String type, String description) {
        return required(name, FieldType.from(type), description);
    }

    public static SchemaField optional(String name, String type, String description) {
        return optional(name, FieldType.from(type), description);
    }

    public static SchemaField required(String name, String type, String description, Object defaultValue) {
        return required(name, FieldType.from(type), description, defaultValue);
    }

    public static SchemaField optional(String name, String type, String description, Object defaultValue) {
        return optional(name, FieldType.from(type), description, defaultValue);
    }

    /** 创建必填敏感字段（密码框）。 */
    public static SchemaField secret(String name, String description) {
        return new SchemaField(name, FieldType.PASSWORD, true, description, name, null, true, List.of(),
                FieldFormat.NONE, null, null);
    }

    public static SchemaField optionalSecret(String name, String description) {
        return new SchemaField(name, FieldType.PASSWORD, false, description, name, null, true, List.of(),
                FieldFormat.NONE, null, null);
    }

    /** 创建带默认值的下拉字段。 */
    public static SchemaField choice(String name, String description, boolean required, Object defaultValue,
            List<String> choices) {
        return new SchemaField(name, FieldType.SELECT, required, description, name, defaultValue, false, choices,
                FieldFormat.NONE, null, null);
    }

    /** 复制本字段并加上闭区间整数范围。 */
    public SchemaField range(int min, int max) {
        return new SchemaField(name, type, required, description, label, defaultValue, secret, choices, format, min,
                max);
    }

    /** 复制本字段并加上整数下界（含）。 */
    public SchemaField atLeast(int min) {
        return new SchemaField(name, type, required, description, label, defaultValue, secret, choices, format, min,
                maximum);
    }
}
