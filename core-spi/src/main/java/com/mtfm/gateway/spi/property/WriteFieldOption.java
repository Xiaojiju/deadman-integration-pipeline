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
 * @param options           该字段可选值（契约枚举如下拉）；VALUE 映射以功能级 writeValueOptions 为准
 * @param format            UI 采值约束（FieldFormat wire code，默认 none）
 * @param valueGenerator    平台值生成器（FieldValueGenerator wire code；空=调用方提供）
 * @param source            值来源 wire：caller / platform / device / constant / mapped
 * @param constant          source=constant 时的固定值
 * @param callerField       source=mapped 时调用方传入的字段名，默认 value
 * @param byteLength        HEX/BINARY 占用字节数；空则打包时按 1
 * @param byteOrder         字节序 big / little，空则 big
 * @param scaleOp           入站换算运算符 add/subtract/multiply/divide
 * @param scaleOperand      入站换算操作数
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
        String byteOrder,
        String scaleOp,
        String scaleOperand
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
        scaleOp = normalizeScaleOp(scaleOp);
        scaleOperand = (scaleOperand == null || scaleOperand.isBlank()) ? null : scaleOperand.trim();
        if (FieldSource.from(source) != FieldSource.CALLER
                || FieldValueGenerators.isPlatformGenerated(valueGenerator)) {
            ignoreRequest = true;
        }
    }

    /**
     * 按字段 path 起 builder。未设置项走规范默认值（string / format=none / source 由 generator 推断）。
     *
     * <p>使用示例：
     * <pre>{@code
     * WriteFieldOption field = WriteFieldOption.builder("params.0")
     *         .description("动作")
     *         .source("mapped")
     *         .callerField("action")
     *         .build();
     * }</pre>
     */
    public static Builder builder(String field) {
        return new Builder(field);
    }

    public static final class Builder {
        private final String field;
        private String description = "";
        private String accessDataType = "string";
        private String transformDataType;
        private boolean ignoreRequest;
        private List<ValueOption> options = List.of();
        private String format = "none";
        private String valueGenerator;
        private String source;
        private String constant;
        private String callerField;
        private Integer byteLength;
        private String byteOrder;
        private String scaleOp;
        private String scaleOperand;

        private Builder(String field) {
            this.field = field;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder accessDataType(String accessDataType) {
            this.accessDataType = accessDataType;
            return this;
        }

        public Builder transformDataType(String transformDataType) {
            this.transformDataType = transformDataType;
            return this;
        }

        public Builder ignoreRequest(boolean ignoreRequest) {
            this.ignoreRequest = ignoreRequest;
            return this;
        }

        public Builder options(List<ValueOption> options) {
            this.options = options;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder valueGenerator(String valueGenerator) {
            this.valueGenerator = valueGenerator;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder constant(String constant) {
            this.constant = constant;
            return this;
        }

        public Builder callerField(String callerField) {
            this.callerField = callerField;
            return this;
        }

        public Builder byteLength(Integer byteLength) {
            this.byteLength = byteLength;
            return this;
        }

        public Builder byteOrder(String byteOrder) {
            this.byteOrder = byteOrder;
            return this;
        }

        public Builder scaleOp(String scaleOp) {
            this.scaleOp = scaleOp;
            return this;
        }

        public Builder scaleOperand(String scaleOperand) {
            this.scaleOperand = scaleOperand;
            return this;
        }

        public WriteFieldOption build() {
            return new WriteFieldOption(
                    field,
                    description,
                    accessDataType,
                    transformDataType,
                    ignoreRequest,
                    options,
                    format,
                    valueGenerator,
                    source,
                    constant,
                    callerField,
                    byteLength,
                    byteOrder,
                    scaleOp,
                    scaleOperand);
        }
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

    private static String normalizeScaleOp(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return raw.trim().toLowerCase();
    }
}
