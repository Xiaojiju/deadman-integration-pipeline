package com.mtfm.gateway.spi.option;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Option 树合并与扁值兼容工具。设备覆盖只替换差异节点，其余沿用产品默认。
 *
 * <p>使用示例：
 * <pre>{@code
 * Option merged = OptionTrees.merge(productOption, deviceOverride);
 * Option fromMap = OptionTrees.fromUnknown(Map.of("offset", 0, "count", 10));
 * Map<String, Object> flat = OptionTrees.toValueMap(productOption);
 * }</pre>
 */
public final class OptionTrees {

    private OptionTrees() {
    }

    /**
     * 产品树与设备差异树合并。override 为 null 则返回产品树。
     */
    public static Option merge(Option product, Option override) {
        if (override == null) {
            return product;
        }
        if (product == null) {
            return override;
        }
        if (product.value() instanceof OptionValue.ObjectNode productObj
                && override.value() instanceof OptionValue.ObjectNode overrideObj) {
            Map<String, Option> merged = new LinkedHashMap<>(productObj.fields());
            overrideObj.fields().forEach((key, child) ->
                    merged.put(key, merge(productObj.fields().get(key), child)));
            return new Option(
                    override.description() != null ? override.description() : product.description(),
                    AccessDataTypeOr(override, product),
                    override.transformDataType() != null ? override.transformDataType() : product.transformDataType(),
                    override.isDefault(),
                    override.mappingValue() != null ? override.mappingValue() : product.mappingValue(),
                    override.logicalNodeEnable() != null ? override.logicalNodeEnable() : product.logicalNodeEnable(),
                    OptionValue.object(merged)
            );
        }
        if (product.value() instanceof OptionValue.ArrayNode productArr
                && override.value() instanceof OptionValue.ArrayNode overrideArr
                && !overrideArr.items().isEmpty()) {
            List<Option> items = new ArrayList<>(overrideArr.items());
            if (items.size() < productArr.items().size()) {
                items.addAll(productArr.items().subList(items.size(), productArr.items().size()));
            }
            return new Option(
                    product.description(),
                    product.accessDataType(),
                    product.transformDataType(),
                    product.isDefault(),
                    product.mappingValue(),
                    product.logicalNodeEnable(),
                    OptionValue.array(items)
            );
        }
        return override;
    }

    /**
     * 将任意对象解析为 Option：String → SCALAR；Map → OBJECT；List → ARRAY。
     */
    public static Option fromUnknown(Object raw) {
        if (raw == null) {
            return Option.scalar("");
        }
        if (raw instanceof Option option) {
            return option;
        }
        if (raw instanceof String text) {
            return Option.fromLegacy(text);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Option> fields = new LinkedHashMap<>();
            map.forEach((key, value) -> fields.put(String.valueOf(key), fromUnknown(value)));
            return new Option(null, com.mtfm.gateway.spi.model.AccessDataType.OBJECT, null,
                    false, null, Boolean.TRUE, OptionValue.object(fields));
        }
        if (raw instanceof List<?> list) {
            List<Option> items = new ArrayList<>();
            for (Object item : list) {
                items.add(fromUnknown(item));
            }
            return new Option(null, com.mtfm.gateway.spi.model.AccessDataType.ARRAY, null,
                    false, null, null, OptionValue.array(items));
        }
        return Option.scalar(String.valueOf(raw));
    }

    /**
     * 将 Option 树压成表单值 Map。OBJECT 展开字段；SCALAR 无键时返回空 Map。
     */
    public static Map<String, Object> toValueMap(Option option) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (option == null) {
            return values;
        }
        if (option.value() instanceof OptionValue.ObjectNode node) {
            node.fields().forEach((key, child) -> {
                if (child == null) {
                    return;
                }
                if (child.value() instanceof OptionValue.Scalar scalar) {
                    values.put(key, scalar.raw());
                } else if (child.value() instanceof OptionValue.ObjectNode) {
                    values.put(key, toValueMap(child));
                } else if (child.value() instanceof OptionValue.ArrayNode arrayNode) {
                    values.put(key, arrayNode.items().stream().map(OptionTrees::scalarOrNested).toList());
                }
            });
        }
        return values;
    }

    /** 将任意对象解析为 Option 并压成 Map。 */
    public static Map<String, Object> toValueMap(Object raw) {
        return toValueMap(fromUnknown(raw));
    }

    private static Object scalarOrNested(Option option) {
        if (option == null) {
            return "";
        }
        if (option.value() instanceof OptionValue.Scalar scalar) {
            return scalar.raw();
        }
        return toValueMap(option);
    }

    private static com.mtfm.gateway.spi.model.AccessDataType AccessDataTypeOr(Option override, Option product) {
        return override.accessDataType() != null ? override.accessDataType() : product.accessDataType();
    }
}
