package com.mtfm.gateway.spi.property;

/**
 * EAV 属性项（对齐旧 property SuperProperty）。
 *
 * @param attribute      属性名
 * @param attributeValue 属性值（一律文本落库，按 dataType 解释）
 * @param dataType       数据类型：string / int / boolean / select / password 等
 * @param description    说明
 */
public record PropertyItem(
        String attribute,
        String attributeValue,
        String dataType,
        String description
) {

    public PropertyItem {
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("attribute 不能为空");
        }
        attributeValue = attributeValue == null ? "" : attributeValue;
        dataType = (dataType == null || dataType.isBlank()) ? "string" : dataType;
        description = description == null ? "" : description;
    }

    public static PropertyItem of(String attribute, Object value, String dataType, String description) {
        return new PropertyItem(attribute, value == null ? "" : String.valueOf(value), dataType, description);
    }

    public static PropertyItem of(String attribute, Object value) {
        return of(attribute, value, inferType(value), "");
    }

    private static String inferType(Object value) {
        if (value instanceof Number) {
            return "int";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return "string";
    }

    /** 按 dataType 解析为运行时 Object。 */
    public Object typedValue() {
        if (attributeValue == null || attributeValue.isBlank()) {
            return null;
        }
        return switch (dataType) {
            case "int", "integer", "INT32" -> {
                try {
                    yield Integer.parseInt(attributeValue.trim());
                } catch (NumberFormatException ex) {
                    yield attributeValue;
                }
            }
            case "boolean", "BOOLEAN" -> "true".equalsIgnoreCase(attributeValue) || "1".equals(attributeValue);
            default -> attributeValue;
        };
    }
}
