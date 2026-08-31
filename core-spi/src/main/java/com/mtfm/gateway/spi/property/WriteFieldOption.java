package com.mtfm.gateway.spi.property;

import java.util.List;

/**
 * STRUCT 写模式下的字段选项（对齐旧 property WriteOption + 子 ValueOption）。
 *
 * @param field             字段名
 * @param description       说明
 * @param accessDataType    原始类型
 * @param transformDataType 转换类型
 * @param ignoreRequest     是否忽略请求体中该字段
 * @param options           该字段可选值
 */
public record WriteFieldOption(
        String field,
        String description,
        String accessDataType,
        String transformDataType,
        boolean ignoreRequest,
        List<ValueOption> options
) {

    public WriteFieldOption {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field 不能为空");
        }
        description = description == null ? "" : description;
        accessDataType = (accessDataType == null || accessDataType.isBlank()) ? "string" : accessDataType;
        transformDataType = (transformDataType == null || transformDataType.isBlank())
                ? accessDataType
                : transformDataType;
        options = options == null ? List.of() : List.copyOf(options);
    }
}
