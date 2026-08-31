package com.mtfm.gateway.spi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * 校验必填字段，缺失时抛出 {@link IllegalArgumentException}。
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
}
