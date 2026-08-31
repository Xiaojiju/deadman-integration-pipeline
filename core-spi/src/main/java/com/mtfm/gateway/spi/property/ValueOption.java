package com.mtfm.gateway.spi.property;

/**
 * 读写值选项（对齐旧 property Option：设备值 ↔ 业务映射值）。
 *
 * @param optionValue        设备侧/协议侧取值（如下发 command=open）
 * @param mappingValue       业务侧映射值，可空则等同 optionValue
 * @param description        说明（如「开门」）
 * @param accessDataType     原始数据类型
 * @param transformDataType  转换后数据类型
 * @param isDefault          是否默认选项
 */
public record ValueOption(
        String optionValue,
        String mappingValue,
        String description,
        String accessDataType,
        String transformDataType,
        Boolean isDefault
) {

    public ValueOption {
        if (optionValue == null || optionValue.isBlank()) {
            throw new IllegalArgumentException("optionValue 不能为空");
        }
        mappingValue = (mappingValue == null || mappingValue.isBlank()) ? optionValue : mappingValue;
        description = description == null ? "" : description;
        accessDataType = (accessDataType == null || accessDataType.isBlank()) ? "string" : accessDataType;
        transformDataType = (transformDataType == null || transformDataType.isBlank())
                ? accessDataType
                : transformDataType;
        if (isDefault == null) {
            isDefault = Boolean.FALSE;
        }
    }

    public static ValueOption of(String optionValue, String description) {
        return new ValueOption(optionValue, optionValue, description, "string", "string", false);
    }

    public static ValueOption ofDefault(String optionValue, String description) {
        return new ValueOption(optionValue, optionValue, description, "string", "string", true);
    }
}
