package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.FieldValueGenerators;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.List;

/** 旧扁平 writeFields → 新 FieldNode 树（兼容迁移）。 */
public final class LegacyFieldAdapter {

    private LegacyFieldAdapter() {
    }

    /** 扁平写字段 → object 根 FieldNode。 */
    public static FieldNode fromWriteFields(List<WriteFieldOption> fields) {
        if (fields == null || fields.isEmpty()) {
            return FieldNode.objectRoot("root", List.of());
        }
        List<FieldNode> children = new ArrayList<>();
        for (WriteFieldOption field : fields) {
            FieldSource source = FieldSource.CALLER;
            if (FieldValueGenerators.isPlatformGenerated(field.valueGenerator())) {
                source = FieldSource.PLATFORM;
            } else if (field.ignoreRequest()) {
                source = FieldSource.PLATFORM;
            }
            List<String> choices = field.options() == null
                    ? List.of()
                    : field.options().stream().map(ValueOption::optionValue).toList();
            String type = field.accessDataType() == null ? "string" : field.accessDataType();
            if (!choices.isEmpty()) {
                type = "select";
            }
            children.add(new FieldNode(
                    field.field(),
                    type,
                    field.format(),
                    source,
                    field.valueGenerator(),
                    null,
                    List.of(),
                    null,
                    choices,
                    field.description()));
        }
        return FieldNode.objectRoot("root", List.copyOf(children));
    }

    /** writeValueOptions → VALUE 映射（optionValue 即 mappingValue，patch 到同名字段）。 */
    public static List<ValueMapping> fromWriteValueOptions(
            List<ValueOption> options, String targetField) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        String field = (targetField == null || targetField.isBlank()) ? "command" : targetField;
        List<ValueMapping> mappings = new ArrayList<>();
        for (ValueOption option : options) {
            String mv = option.mappingValue() != null ? option.mappingValue() : option.optionValue();
            mappings.add(ValueMapping.patch(
                    mv,
                    option.description(),
                    List.of(new FieldPatch(field, option.optionValue()))));
        }
        return List.copyOf(mappings);
    }

    public static PayloadMode payloadModeFromWriteAccess(String writeAccessType, List<ValueOption> valueOptions) {
        if ("VALUE".equalsIgnoreCase(writeAccessType) && valueOptions != null && !valueOptions.isEmpty()) {
            return PayloadMode.VALUE;
        }
        return PayloadMode.STRUCT;
    }
}
