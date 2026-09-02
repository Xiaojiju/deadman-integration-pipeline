package com.mtfm.gateway.spi.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mtfm.gateway.spi.model.FieldType;

import java.util.List;

/**
 * 协议 JSON 字段树节点。
 *
 * @param name            字段名（根节点可为 root）
 * @param type            FieldType wire code
 * @param format          FieldFormat wire code
 * @param source          值来源
 * @param valueGenerator  PLATFORM 时生成器 wire code
 * @param constant        CONSTANT 固定值
 * @param children        object 子字段 / array 按下标的元素
 * @param element         array 元素 schema
 * @param choices         select 选项
 * @param description     说明
 * @param byteLength      HEX/BINARY 占用字节数
 * @param byteOrder       字节序 big / little
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldNode(
        String name,
        String type,
        String format,
        FieldSource source,
        String valueGenerator,
        Object constant,
        List<FieldNode> children,
        FieldNode element,
        List<String> choices,
        String description,
        Integer byteLength,
        String byteOrder
) {

    public FieldNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        type = (type == null || type.isBlank()) ? FieldType.STRING.code() : type;
        format = (format == null || format.isBlank()) ? "none" : format;
        source = source == null ? FieldSource.CALLER : source;
        children = children == null ? List.of() : List.copyOf(children);
        choices = choices == null ? List.of() : List.copyOf(choices);
        description = description == null ? "" : description;
        if (byteLength != null && byteLength <= 0) {
            byteLength = null;
        }
        if (byteOrder != null && byteOrder.isBlank()) {
            byteOrder = null;
        }
    }

    /** 兼容旧 10 参构造（无 byteLength / byteOrder）。 */
    public FieldNode(
            String name,
            String type,
            String format,
            FieldSource source,
            String valueGenerator,
            Object constant,
            List<FieldNode> children,
            FieldNode element,
            List<String> choices,
            String description) {
        this(name, type, format, source, valueGenerator, constant, children, element, choices, description, null, null);
    }

    public boolean isObject() {
        if (isArray()) {
            return false;
        }
        return "object".equalsIgnoreCase(type) || "json".equalsIgnoreCase(type) || !children.isEmpty();
    }

    public boolean isArray() {
        return "array".equalsIgnoreCase(type) || element != null;
    }

    /** 创建 object 根。 */
    public static FieldNode objectRoot(String name, List<FieldNode> children) {
        return new FieldNode(name, "object", "none", FieldSource.CALLER, null, null, children, null, List.of(), "",
                null, null);
    }

    /** 扁平字段叶子。 */
    public static FieldNode leaf(
            String name,
            String type,
            FieldSource source,
            String format,
            String valueGenerator,
            Object constant,
            String description) {
        return new FieldNode(
                name, type, format, source, valueGenerator, constant,
                List.of(), null, List.of(), description, null, null);
    }
}
