package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.spi.model.FieldFormat;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.payload.FieldSource;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能表单投影：writeFields / properties → 调用方 schema。与 FIXED/OPEN/CONTRACT 约束无关。
 */
final class CatalogFunctionSchemas {

    private CatalogFunctionSchemas() {
    }

    static Map<String, Object> constantsOf(List<WriteFieldOption> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (WriteFieldOption field : fields == null ? List.<WriteFieldOption>of() : fields) {
            if (field.constant() != null && !field.constant().isBlank()) {
                values.put(field.field(), field.constant());
            }
        }
        return values;
    }

    static List<WriteFieldOption> filterKnownFields(List<WriteFieldOption> existing, FunctionTemplate template) {
        if (existing == null || existing.isEmpty() || template == null || template.parameters() == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (SchemaField field : template.parameters()) {
            names.add(field.name());
        }
        return existing.stream()
                .filter(field -> field != null && names.contains(field.field()))
                .toList();
    }

    static List<ValueOption> flattenChoiceOptions(Map<String, List<ValueOption>> choices) {
        if (choices == null || choices.isEmpty()) {
            return List.of();
        }
        if (choices.size() == 1) {
            return List.copyOf(choices.values().iterator().next());
        }
        return List.of();
    }

    static List<ValueOption> flattenWriteFieldOptions(List<WriteFieldOption> writeFields) {
        if (writeFields == null || writeFields.isEmpty()) {
            return List.of();
        }
        List<WriteFieldOption> withOptions = writeFields.stream()
                .filter(field -> FieldSource.from(field.source()) == FieldSource.MAPPED
                        && field.options() != null
                        && !field.options().isEmpty())
                .toList();
        if (withOptions.isEmpty()) {
            return List.of();
        }
        if (withOptions.size() == 1) {
            return withOptions.get(0).options();
        }
        return List.of();
    }

    static List<SchemaField> schemaFromWriteFields(List<WriteFieldOption> writeFields, boolean callerOnly) {
        if (writeFields == null || writeFields.isEmpty()) {
            return List.of();
        }
        Map<String, SchemaField> byName = new LinkedHashMap<>();
        for (WriteFieldOption field : writeFields) {
            if (field == null || field.field() == null || field.field().isBlank()) {
                continue;
            }
            FieldSource source = FieldSource.from(field.source());
            if (callerOnly) {
                if (source == FieldSource.PLATFORM || source == FieldSource.DEVICE || source == FieldSource.CONSTANT) {
                    continue;
                }
                if (field.platformGenerated()) {
                    continue;
                }
            }
            boolean mapped = source == FieldSource.MAPPED;
            boolean caller = source == FieldSource.CALLER;
            String name;
            if (field.callerField() != null && !field.callerField().isBlank() && (mapped || caller)) {
                name = field.callerField();
            } else if (mapped) {
                name = "value";
            } else {
                name = field.field();
            }
            List<String> choices = field.options() == null
                    ? List.of()
                    : field.options().stream()
                            .map(option -> mapped
                                    ? (option.mappingValue() == null || option.mappingValue().isBlank()
                                            ? option.optionValue()
                                            : option.mappingValue())
                                    : option.optionValue())
                            .filter(v -> v != null && !v.isBlank())
                            .toList();
            SchemaField existing = byName.get(name);
            if (existing != null) {
                List<String> merged = new ArrayList<>(existing.choices() == null ? List.of() : existing.choices());
                for (String choice : choices) {
                    if (!merged.contains(choice)) {
                        merged.add(choice);
                    }
                }
                byName.put(name, new SchemaField(
                        existing.name(),
                        existing.type(),
                        existing.required(),
                        existing.description(),
                        existing.label(),
                        existing.defaultValue(),
                        existing.secret(),
                        List.copyOf(merged),
                        existing.format(),
                        existing.minimum(),
                        existing.maximum()));
                continue;
            }
            FieldType type = FieldType.from(field.accessDataType());
            FieldFormat format = FieldFormat.from(field.format());
            String description = field.description() == null ? "" : field.description();
            byName.put(name, new SchemaField(
                    name,
                    type,
                    mapped || caller,
                    description,
                    name,
                    null,
                    type == FieldType.PASSWORD,
                    choices,
                    format,
                    null,
                    null));
        }
        return List.copyOf(byName.values());
    }

    /**
     * 把 writeValueOptions 的业务值补到 MAPPED 调用方字段的 choices，
     * 指令表单才能按枚举渲染按钮；CALLER 透传字段保持无 choices（输入框）。
     */
    static List<SchemaField> applyWriteValueOptionChoices(
            List<SchemaField> schema,
            List<WriteFieldOption> writeFields,
            List<ValueOption> writeValueOptions) {
        if (writeValueOptions == null || writeValueOptions.isEmpty()) {
            return schema == null ? List.of() : schema;
        }
        List<String> choices = new ArrayList<>();
        for (ValueOption option : writeValueOptions) {
            String choice = option.mappingValue() == null || option.mappingValue().isBlank()
                    ? option.optionValue()
                    : option.mappingValue();
            if (choice != null && !choice.isBlank() && !choices.contains(choice)) {
                choices.add(choice);
            }
        }
        if (choices.isEmpty()) {
            return schema == null ? List.of() : schema;
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (writeFields != null) {
            for (WriteFieldOption field : writeFields) {
                if (FieldSource.from(field.source()) != FieldSource.MAPPED) {
                    continue;
                }
                targets.add(field.callerField() == null || field.callerField().isBlank()
                        ? "value"
                        : field.callerField());
            }
        }
        if (targets.isEmpty()) {
            targets.add("value");
        }
        List<SchemaField> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SchemaField field : schema == null ? List.<SchemaField>of() : schema) {
            if (targets.contains(field.name())) {
                result.add(withChoices(field, choices));
                seen.add(field.name());
            } else {
                result.add(field);
            }
        }
        for (String target : targets) {
            if (seen.contains(target)) {
                continue;
            }
            result.add(new SchemaField(
                    target,
                    FieldType.STRING,
                    true,
                    "",
                    target,
                    choices.get(0),
                    false,
                    List.copyOf(choices),
                    FieldFormat.NONE,
                    null,
                    null));
        }
        return List.copyOf(result);
    }

    static List<SchemaField> schemaFromProperties(List<PropertyItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item.attribute() != null && !item.attribute().isBlank())
                .map(item -> SchemaField.optional(
                        item.attribute(),
                        FieldType.from(item.dataType()),
                        item.description() == null ? "" : item.description()))
                .toList();
    }

    static String resolvePayloadMode(ProductFunctionEntity function, List<ValueOption> writeOptions) {
        if (function.getPayloadMode() != null && !function.getPayloadMode().isBlank()) {
            return function.getPayloadMode();
        }
        if (writeOptions != null && !writeOptions.isEmpty()
                && "VALUE".equalsIgnoreCase(function.getWriteAccessType())) {
            return PayloadMode.VALUE.wire();
        }
        return PayloadMode.STRUCT.wire();
    }

    private static SchemaField withChoices(SchemaField field, List<String> choices) {
        return new SchemaField(
                field.name(),
                field.type(),
                field.required(),
                field.description(),
                field.label(),
                field.defaultValue() != null ? field.defaultValue() : choices.get(0),
                field.secret(),
                List.copyOf(choices),
                field.format(),
                field.minimum(),
                field.maximum());
    }
}
