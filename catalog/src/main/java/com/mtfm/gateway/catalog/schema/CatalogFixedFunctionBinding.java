package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FIXED 能力：功能 ID 与字段名由模板锁死，只允许改说明与默认值。
 */
final class CatalogFixedFunctionBinding {

    private CatalogFixedFunctionBinding() {
    }

    static List<PropertyItem> constrainFixedProperties(List<PropertyItem> items, FunctionTemplate template) {
        Map<String, SchemaField> allowed = new LinkedHashMap<>();
        for (SchemaField field : template.parameters()) {
            allowed.put(field.name(), field);
        }
        List<PropertyItem> result = new ArrayList<>();
        for (PropertyItem item : items == null ? List.<PropertyItem>of() : items) {
            SchemaField field = allowed.get(item.attribute());
            if (field == null) {
                throw new IllegalArgumentException(
                        "FIXED 功能不允许自定义属性: " + item.attribute() + "（功能 " + template.functionId() + "）");
            }
            if (field.choices() != null && !field.choices().isEmpty()
                    && !field.choices().contains(item.attributeValue())) {
                throw new IllegalArgumentException(
                        "属性 " + item.attribute() + " 取值必须是模板规定项: " + field.choices());
            }
            SchemaValidator.requireConstraints(
                    List.of(field),
                    item.attributeValue() == null ? Map.of() : Map.of(field.name(), item.attributeValue()),
                    "功能属性");
            String description = item.description() == null || item.description().isBlank()
                    ? field.description()
                    : item.description();
            result.add(new PropertyItem(
                    field.name(),
                    item.attributeValue(),
                    field.type().code(),
                    description));
        }
        return List.copyOf(result);
    }

    static List<WriteFieldOption> templateWriteFields(FunctionTemplate template) {
        if (template == null || template.parameters() == null || template.parameters().isEmpty()) {
            return List.of();
        }
        List<WriteFieldOption> fields = new ArrayList<>();
        for (SchemaField field : template.parameters()) {
            fields.add(WriteFieldOption.builder(field.name())
                    .description(field.description())
                    .accessDataType(field.type().code())
                    .transformDataType(field.type().code())
                    .options(PropertySchemas.choicesToValueOptions(field))
                    .format(field.format().code())
                    .build());
        }
        return List.copyOf(fields);
    }

    static List<WriteFieldOption> resolveFixedWriteFields(
            List<WriteFieldOption> requested, Optional<FunctionTemplate> template) {
        if (template.isEmpty()) {
            return List.of();
        }
        if (requested == null || requested.isEmpty()) {
            return templateWriteFields(template.get());
        }
        return constrainFixedWriteFields(requested, template.get());
    }

    static List<WriteFieldOption> constrainFixedWriteFields(
            List<WriteFieldOption> requested, FunctionTemplate template) {
        Map<String, WriteFieldOption> byField = new LinkedHashMap<>();
        for (WriteFieldOption field : requested == null ? List.<WriteFieldOption>of() : requested) {
            byField.put(field.field(), field);
        }
        List<WriteFieldOption> result = new ArrayList<>();
        for (SchemaField schemaField : template.parameters()) {
            WriteFieldOption req = byField.remove(schemaField.name());
            List<ValueOption> allowedOptions = PropertySchemas.choicesToValueOptions(schemaField);
            String description = schemaField.description();
            List<ValueOption> options = allowedOptions;
            if (req != null) {
                if (req.description() != null && !req.description().isBlank()) {
                    description = req.description();
                }
                if (req.options() != null && !req.options().isEmpty()) {
                    options = mergeFixedFieldOptions(req.options(), allowedOptions);
                }
            }
            result.add(WriteFieldOption.builder(schemaField.name())
                    .description(description)
                    .accessDataType(schemaField.type().code())
                    .transformDataType(schemaField.type().code())
                    .ignoreRequest(req != null && req.ignoreRequest())
                    .options(options)
                    .format(req != null && req.format() != null && !req.format().isBlank()
                            ? req.format()
                            : schemaField.format().code())
                    .valueGenerator(req != null ? req.valueGenerator() : null)
                    .build());
        }
        if (!byField.isEmpty()) {
            throw new IllegalArgumentException(
                    "FIXED 功能不允许自定义写字段: " + byField.keySet() + "（功能 " + template.functionId() + "）");
        }
        return List.copyOf(result);
    }

    static List<ValueOption> mergeFixedFieldOptions(List<ValueOption> requested, List<ValueOption> allowed) {
        if (allowed.isEmpty()) {
            if (requested != null && !requested.isEmpty()) {
                throw new IllegalArgumentException("该写字段未定义枚举选项，不允许自定义 ValueOption");
            }
            return List.of();
        }
        Map<String, ValueOption> allowedByValue = new LinkedHashMap<>();
        for (ValueOption option : allowed) {
            allowedByValue.put(option.optionValue(), option);
        }
        Map<String, ValueOption> resultByValue = new LinkedHashMap<>();
        for (ValueOption option : requested == null ? List.<ValueOption>of() : requested) {
            ValueOption base = allowedByValue.get(option.optionValue());
            if (base == null) {
                throw new IllegalArgumentException(
                        "FIXED 功能选项取值非法: " + option.optionValue() + "，允许: " + allowedByValue.keySet());
            }
            String description = option.description() == null || option.description().isBlank()
                    ? base.description()
                    : option.description();
            Boolean isDefault = option.isDefault() != null ? option.isDefault() : base.isDefault();
            resultByValue.put(option.optionValue(), new ValueOption(
                    base.optionValue(),
                    option.mappingValue() == null || option.mappingValue().isBlank()
                            ? base.mappingValue()
                            : option.mappingValue(),
                    description,
                    base.accessDataType(),
                    base.transformDataType(),
                    isDefault));
        }
        for (ValueOption option : allowed) {
            resultByValue.putIfAbsent(option.optionValue(), option);
        }
        return List.copyOf(resultByValue.values());
    }

    static List<ValueOption> constrainFixedValueOptions(List<ValueOption> requested, FunctionTemplate template) {
        Map<String, List<ValueOption>> byField = PropertySchemas.choiceOptionsByField(template.parameters());
        List<ValueOption> allowed = CatalogFunctionSchemas.flattenChoiceOptions(byField);
        if (allowed.isEmpty()) {
            if (requested != null && !requested.isEmpty()) {
                throw new IllegalArgumentException(
                        "FIXED 功能 " + template.functionId() + " 未定义枚举选项，不允许自定义 ValueOption");
            }
            return List.of();
        }
        if (requested == null || requested.isEmpty()) {
            return allowed;
        }
        Set<String> allowedValues = allowed.stream()
                .map(option -> option.optionValue())
                .collect(Collectors.toSet());
        List<ValueOption> result = new ArrayList<>();
        for (ValueOption option : requested) {
            if (!allowedValues.contains(option.optionValue())) {
                throw new IllegalArgumentException(
                        "FIXED 功能选项取值非法: " + option.optionValue() + "，允许: " + allowedValues);
            }
            result.add(option);
        }
        Set<String> submitted = result.stream()
                .map(option -> option.optionValue())
                .collect(Collectors.toSet());
        for (ValueOption option : allowed) {
            if (!submitted.contains(option.optionValue())) {
                result.add(option);
            }
        }
        return List.copyOf(result);
    }
}
