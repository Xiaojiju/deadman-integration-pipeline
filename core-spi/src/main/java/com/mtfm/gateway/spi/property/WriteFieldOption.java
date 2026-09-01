package com.mtfm.gateway.spi.property;

import java.util.List;

/**
 * STRUCT 写模式下的字段选项（对齐旧 property WriteOption + 子 ValueOption）。
 *
 * @param field             字段名
 * @param description       说明
 * @param accessDataType    原始类型（FieldType wire code）
 * @param transformDataType 转换类型
 * @param ignoreRequest     是否忽略请求体中该字段（平台生成时自动为 true）
 * @param options           该字段可选值
 * @param format            UI 采值约束（FieldFormat wire code，默认 none）
 * @param valueGenerator    平台值生成器（FieldValueGenerator wire code；空=调用方提供）
 */
public record WriteFieldOption(
        String field,
        String description,
        String accessDataType,
        String transformDataType,
        boolean ignoreRequest,
        List<ValueOption> options,
        String format,
        String valueGenerator
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
        format = (format == null || format.isBlank()) ? "none" : format;
        valueGenerator = normalizeGenerator(valueGenerator);
        if (FieldValueGenerators.isPlatformGenerated(valueGenerator)) {
            ignoreRequest = true;
        }
    }

    /** 兼容旧 7 参构造（无 valueGenerator）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options,
            String format) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, format, null);
    }

    /** 兼容旧 6 参构造（format=none，无 valueGenerator）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, "none", null);
    }

    /** 是否平台自动生成，调用方无需传递。 */
    public boolean platformGenerated() {
        return FieldValueGenerators.isPlatformGenerated(valueGenerator);
    }

    private static String normalizeGenerator(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return raw.trim();
    }
}
