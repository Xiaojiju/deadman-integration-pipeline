package com.mtfm.gateway.spi.property;

import com.mtfm.gateway.spi.payload.FieldSource;

import java.util.List;

/**
 * 写/读字段选项（扁平 path，可含点分下标如 {@code params.0}）。
 *
 * @param field             字段 path
 * @param description       说明
 * @param accessDataType    原始类型（FieldType wire code）
 * @param transformDataType 转换类型
 * @param ignoreRequest     是否忽略请求体中该字段（非 caller 时自动为 true）
 * @param options           该字段可选值；MAPPED 叶子上即 VALUE 映射
 * @param format            UI 采值约束（FieldFormat wire code，默认 none）
 * @param valueGenerator    平台值生成器（FieldValueGenerator wire code；空=调用方提供）
 * @param source            值来源 wire：caller / platform / device / constant / mapped
 * @param constant          source=constant 时的固定值
 * @param callerField       source=mapped 时调用方传入的字段名，默认 value
 * @param byteLength        HEX/BINARY 占用字节数；空则打包时按 1
 * @param byteOrder         字节序 big / little，空则 big
 */
public record WriteFieldOption(
        String field,
        String description,
        String accessDataType,
        String transformDataType,
        boolean ignoreRequest,
        List<ValueOption> options,
        String format,
        String valueGenerator,
        String source,
        String constant,
        String callerField,
        Integer byteLength,
        String byteOrder
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
        constant = (constant == null || constant.isBlank()) ? null : constant;
        source = normalizeSource(source, valueGenerator, ignoreRequest, constant);
        callerField = normalizeCallerField(source, callerField);
        byteLength = normalizeByteLength(byteLength);
        byteOrder = normalizeByteOrder(byteOrder);
        if (FieldSource.from(source) != FieldSource.CALLER
                || FieldValueGenerators.isPlatformGenerated(valueGenerator)) {
            ignoreRequest = true;
        }
    }

    /** 兼容旧 11 参构造（无 byteLength / byteOrder）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options,
            String format,
            String valueGenerator,
            String source,
            String constant,
            String callerField) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, format, valueGenerator,
                source, constant, callerField, null, null);
    }

    /** 兼容旧 10 参构造（无 callerField）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options,
            String format,
            String valueGenerator,
            String source,
            String constant) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, format, valueGenerator,
                source, constant, null, null, null);
    }

    /** 兼容旧 8 参构造（无 source / constant）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options,
            String format,
            String valueGenerator) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, format, valueGenerator,
                null, null);
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
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, format, null, null, null);
    }

    /** 兼容旧 6 参构造（format=none，无 valueGenerator）。 */
    public WriteFieldOption(
            String field,
            String description,
            String accessDataType,
            String transformDataType,
            boolean ignoreRequest,
            List<ValueOption> options) {
        this(field, description, accessDataType, transformDataType, ignoreRequest, options, "none", null, null, null);
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

    private static String normalizeSource(
            String source, String valueGenerator, boolean ignoreRequest, String constant) {
        if (source != null && !source.isBlank()) {
            return FieldSource.from(source).wire();
        }
        if (constant != null && !constant.isBlank()) {
            return FieldSource.CONSTANT.wire();
        }
        if (FieldValueGenerators.isPlatformGenerated(valueGenerator)) {
            return FieldSource.PLATFORM.wire();
        }
        if (ignoreRequest) {
            return FieldSource.PLATFORM.wire();
        }
        return FieldSource.CALLER.wire();
    }

    private static String normalizeCallerField(String source, String callerField) {
        if (FieldSource.from(source) != FieldSource.MAPPED) {
            return (callerField == null || callerField.isBlank()) ? null : callerField.trim();
        }
        if (callerField == null || callerField.isBlank()) {
            return "value";
        }
        return callerField.trim();
    }

    private static Integer normalizeByteLength(Integer byteLength) {
        if (byteLength == null || byteLength <= 0) {
            return null;
        }
        if (byteLength > 32) {
            throw new IllegalArgumentException("byteLength 须在 1–32 之间: " + byteLength);
        }
        return byteLength;
    }

    private static String normalizeByteOrder(String byteOrder) {
        if (byteOrder == null || byteOrder.isBlank()) {
            return null;
        }
        String trimmed = byteOrder.trim().toLowerCase();
        if ("little".equals(trimmed) || "le".equals(trimmed)) {
            return "little";
        }
        return "big";
    }
}
