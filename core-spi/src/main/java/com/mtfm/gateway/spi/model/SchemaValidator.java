package com.mtfm.gateway.spi.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按能力 schema 校验通道 connection / 地址片 / 功能参数。
 *
 * <p>使用示例：
 * <pre>{@code
 * SchemaValidator.require(connectionSchema, connectionValues, "connection");
 * List<String> missing = SchemaValidator.missingRequired(addressSchema, addressValues);
 * if (!missing.isEmpty()) { ... }
 * }</pre>
 */
public final class SchemaValidator {

    private SchemaValidator() {
    }

    /**
     * 校验必填、未知字段、基础类型与枚举取值；失败时抛出 {@link IllegalArgumentException}。
     *
     * @param schema schema 字段列表
     * @param values 待校验值
     * @param scope  范围说明（用于错误消息）
     */
    public static void require(List<SchemaField> schema, Map<String, ?> values, String scope) {
        List<String> missing = missingRequired(schema, values);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(scope + " 缺少必填字段: " + String.join(", ", missing));
        }
        requireKnownFields(schema, values, scope);
        requireTypes(schema, values, scope);
        requireChoices(schema, values, scope);
    }

    /** 返回缺失的必填字段名列表。 */
    public static List<String> missingRequired(List<SchemaField> schema, Map<String, ?> values) {
        Map<String, ?> safe = values == null ? Map.of() : values;
        List<String> missing = new ArrayList<>();
        if (schema == null) {
            return missing;
        }
        for (SchemaField field : schema) {
            if (!field.required()) {
                continue;
            }
            Object value = safe.get(field.name());
            if (value == null || String.valueOf(value).isBlank()) {
                missing.add(field.name());
            }
        }
        return missing;
    }

    /**
     * schema 非空时拒绝未声明字段，避免连接/地址片塞入运行时不认识的键。
     */
    public static void requireKnownFields(List<SchemaField> schema, Map<String, ?> values, String scope) {
        if (schema == null || schema.isEmpty() || values == null || values.isEmpty()) {
            return;
        }
        Set<String> known = new LinkedHashSet<>();
        for (SchemaField field : schema) {
            known.add(field.name());
        }
        List<String> unknown = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key == null || key.isBlank() || known.contains(key)) {
                continue;
            }
            unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(scope + " 含未声明字段: " + String.join(", ", unknown));
        }
    }

    /**
     * 校验 INT / BOOLEAN 取值形态；表单常以字符串提交，允许可解析的文本。
     */
    public static void requireTypes(List<SchemaField> schema, Map<String, ?> values, String scope) {
        Map<String, ?> safe = values == null ? Map.of() : values;
        if (schema == null) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        for (SchemaField field : schema) {
            Object value = safe.get(field.name());
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            FieldType type = field.type();
            if (type == FieldType.INT && !isInteger(value)) {
                invalid.add(field.name() + "（期望整数）");
            } else if (type == FieldType.BOOLEAN && !isBoolean(value)) {
                invalid.add(field.name() + "（期望布尔）");
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(scope + " 类型不合法: " + String.join(", ", invalid));
        }
    }

    /**
     * 校验字段值是否落在 choices 内（有 choices 的字段）；不合法时抛出异常。
     */
    public static void requireChoices(List<SchemaField> schema, Map<String, ?> values, String scope) {
        Map<String, ?> safe = values == null ? Map.of() : values;
        if (schema == null) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        for (SchemaField field : schema) {
            if (field.choices() == null || field.choices().isEmpty()) {
                continue;
            }
            Object value = safe.get(field.name());
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            String text = String.valueOf(value);
            if (!field.choices().contains(text)) {
                invalid.add(field.name() + "（允许: " + String.join("/", field.choices()) + "）");
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(scope + " 取值不合法: " + String.join(", ", invalid));
        }
    }

    static boolean isInteger(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger) {
            return true;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            return !Double.isNaN(raw) && !Double.isInfinite(raw) && raw == Math.rint(raw);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return false;
        }
        try {
            new BigInteger(text);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static boolean isBoolean(Object value) {
        if (value instanceof Boolean) {
            return true;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text)
                || "false".equalsIgnoreCase(text)
                || "1".equals(text)
                || "0".equals(text);
    }
}
