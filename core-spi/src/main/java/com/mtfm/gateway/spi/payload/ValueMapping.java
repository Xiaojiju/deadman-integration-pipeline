package com.mtfm.gateway.spi.payload;

import java.util.List;

/**
 * VALUE 模式：调用方简值 → 协议字段树。
 *
 * <p>使用示例：
 * <pre>{@code
 * ValueMapping patch = ValueMapping.patch("open", "开门", List.of(new FieldPatch("params.0", "1")));
 * ValueMapping fill = ValueMapping.fillRoot("open", "开门", "open");
 * }</pre>
 *
 * @param mappingValue 调用方传入的业务值，如 open / 1
 * @param description  说明
 * @param target       FILL_ROOT 或 PATCH_FIELDS
 * @param rootValue    target=FILL_ROOT 时的整包值（可为字符串或对象）
 * @param patches      target=PATCH_FIELDS 时的 path 列表
 * @param callerField  调用方参数名，默认 {@code value}；多个映射可绑定不同字段
 */
public record ValueMapping(
        String mappingValue,
        String description,
        MappingTarget target,
        Object rootValue,
        List<FieldPatch> patches,
        String callerField
) {

    public ValueMapping {
        if (mappingValue == null || mappingValue.isBlank()) {
            throw new IllegalArgumentException("mappingValue 不能为空");
        }
        description = description == null ? "" : description;
        target = target == null ? MappingTarget.PATCH_FIELDS : target;
        patches = patches == null ? List.of() : List.copyOf(patches);
        callerField = (callerField == null || callerField.isBlank())
                ? CommandAssembler.CALLER_VALUE_KEY
                : callerField.trim();
    }

    public static ValueMapping patch(String mappingValue, String description, List<FieldPatch> patches) {
        return patch(CommandAssembler.CALLER_VALUE_KEY, mappingValue, description, patches);
    }

    public static ValueMapping patch(
            String callerField, String mappingValue, String description, List<FieldPatch> patches) {
        return new ValueMapping(
                mappingValue, description, MappingTarget.PATCH_FIELDS, null, patches, callerField);
    }

    public static ValueMapping fillRoot(String mappingValue, String description, Object rootValue) {
        return new ValueMapping(
                mappingValue, description, MappingTarget.FILL_ROOT, rootValue, List.of(),
                CommandAssembler.CALLER_VALUE_KEY);
    }
}
