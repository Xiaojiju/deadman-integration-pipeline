package com.mtfm.gateway.spi.model;

import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.option.OptionTrees;
import com.mtfm.gateway.spi.option.OptionValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 与当前值装配成表单字段的工具类。
 *
 * <p>使用示例：
 * <pre>{@code
 * List<FormField> form = SchemaForms.bind(connectionSchema, Map.of("host", "10.0.0.1"));
 * Map<String, Object> defaults = SchemaForms.defaults(readParams);
 * Option option = SchemaForms.defaultsAsOption(readParams);
 * }</pre>
 */
public final class SchemaForms {

    private SchemaForms() {
    }

    /** 将 schema 与 Map 值绑定为表单字段列表。 */
    public static List<FormField> bind(List<SchemaField> schema, Map<String, ?> values) {
        Map<String, ?> safe = values == null ? Map.of() : values;
        List<FormField> fields = new ArrayList<>();
        for (SchemaField item : schema) {
            fields.add(FormField.from(item, safe.get(item.name())));
        }
        return List.copyOf(fields);
    }

    /** 将 schema 与 Option 树绑定为表单字段列表。 */
    public static List<FormField> bind(List<SchemaField> schema, Option option) {
        return bind(schema, OptionTrees.toValueMap(option));
    }

    /**
     * schema 中带显式 defaultValue 的字段写成可持久化 Map，供产品功能预填。
     * <p>不含「无默认值的必填项」——那些属于调用时参数，挂载产品功能时不必填齐。
     */
    public static Map<String, Object> defaults(List<SchemaField> schema) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (schema == null) {
            return values;
        }
        for (SchemaField item : schema) {
            if (item.defaultValue() != null) {
                values.put(item.name(), item.defaultValue());
            }
        }
        return values;
    }

    /** 将 schema 默认值转为 Option 树。 */
    public static Option defaultsAsOption(List<SchemaField> schema) {
        return OptionTrees.fromUnknown(defaults(schema));
    }

    /** 从 Option 提取标量或嵌套 Map 值。 */
    public static Object scalarValue(Option option) {
        if (option == null) {
            return null;
        }
        if (option.value() instanceof OptionValue.Scalar scalar) {
            return scalar.raw();
        }
        return OptionTrees.toValueMap(option);
    }
}
