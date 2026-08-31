package com.mtfm.gateway.spi.option;

import com.mtfm.gateway.spi.model.AccessDataType;

/**
 * 功能参数树节点。产品侧是 schema + 默认值；设备覆盖只存差异节点。
 *
 * <p>取值由 {@link OptionValue} 表达：标量、对象或数组。
 *
 * <p>使用示例：
 * <pre>{@code
 * Option scalar = Option.scalar("100");
 * Option object = new Option(null, AccessDataType.OBJECT, null, false, null, true,
 *         OptionValue.object(Map.of(
 *                 "offset", Option.scalar("0"),
 *                 "count", Option.scalar("10"))));
 * }</pre>
 *
 * @param description        节点说明
 * @param accessDataType     访问数据类型
 * @param transformDataType  转换目标类型，可空
 * @param isDefault          是否为默认值节点
 * @param mappingValue       映射值，可空
 * @param logicalNodeEnable  逻辑节点是否启用（OBJECT 默认 true）
 * @param value              实际取值
 */
public record Option(
        String description,
        AccessDataType accessDataType,
        AccessDataType transformDataType,
        boolean isDefault,
        String mappingValue,
        Boolean logicalNodeEnable,
        OptionValue value
) {

    public Option {
        if (value == null) {
            value = OptionValue.scalar("");
        }
        if (accessDataType == null) {
            accessDataType = inferType(value);
        }
        if (value instanceof OptionValue.ObjectNode && logicalNodeEnable == null) {
            logicalNodeEnable = Boolean.TRUE;
        }
    }

    /** 创建标量 Option。 */
    public static Option scalar(String raw) {
        return new Option(null, AccessDataType.CHAR16, null, false, null, null, OptionValue.scalar(raw));
    }

    /**
     * 旧扁字符串反序列化为 SCALAR，保证可迁移。
     */
    public static Option fromLegacy(String raw) {
        return scalar(raw == null ? "" : raw);
    }

    /** 是否为 OBJECT 节点。 */
    public boolean isObject() {
        return value instanceof OptionValue.ObjectNode;
    }

    /** 是否为 ARRAY 节点。 */
    public boolean isArray() {
        return value instanceof OptionValue.ArrayNode;
    }

    /** 是否为 SCALAR 节点。 */
    public boolean isScalar() {
        return value instanceof OptionValue.Scalar;
    }

    private static AccessDataType inferType(OptionValue value) {
        if (value instanceof OptionValue.ObjectNode) {
            return AccessDataType.OBJECT;
        }
        if (value instanceof OptionValue.ArrayNode) {
            return AccessDataType.ARRAY;
        }
        return AccessDataType.CHAR16;
    }
}
