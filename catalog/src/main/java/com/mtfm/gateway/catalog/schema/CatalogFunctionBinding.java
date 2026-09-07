package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldFormat;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.payload.FieldSource;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FIXED / OPEN / CONTRACT 功能字段绑定与约束。
 */
final class CatalogFunctionBinding {

    private CatalogFunctionBinding() {
    }

    record FunctionOptionPlan(
            ValueAccessType writeAccess,
            List<WriteFieldOption> writeFields,
            List<WriteFieldOption> readFields,
            List<ValueOption> writeValueOptions,
            List<ValueOption> readValueOptions) {
    }

    static FunctionOptionPlan bindRequest(
            CapabilityDescriptor descriptor,
            Optional<FunctionTemplate> template,
            ProductFunctionWriteRequest request,
            String accessType,
            boolean openLockedEmpty) {
        ValueAccessType writeAccess;
        List<ValueOption> writeValueOptions;
        List<WriteFieldOption> writeFields;
        List<WriteFieldOption> readFields;
        if (descriptor.fixedFunctions()) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = resolveFixedWriteFields(request.writeFields(), template);
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (openLockedEmpty) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = List.of();
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (descriptor.contractedParameters()) {
            writeAccess = resolveWriteAccess(request);
            String access = accessType != null && !accessType.isBlank()
                    ? accessType
                    : template.map(t -> t.accessType()).orElse("WRITE");
            FunctionTemplate contract = requireContractTemplate(descriptor, access);
            PayloadMode mode = writeAccess == ValueAccessType.VALUE ? PayloadMode.VALUE : PayloadMode.STRUCT;
            boolean isRead = "READ".equalsIgnoreCase(access);
            writeFields = isRead ? List.of() : bindContractFields(contract, request.writeFields(), mode);
            readFields = isRead ? bindContractFields(contract, request.readFields(), mode) : List.of();
            writeValueOptions = isRead
                    ? List.of()
                    : constrainContractValueOptions(
                            request.writeValueOptions() == null ? List.of() : request.writeValueOptions(),
                            contract,
                            mode);
            SchemaValidator.requireConstraints(contract.parameters(), constantsOf(writeFields), "功能参数");
            SchemaValidator.requireConstraints(contract.parameters(), constantsOf(readFields), "功能参数");
        } else {
            writeAccess = resolveWriteAccess(request);
            boolean isRead = "READ".equalsIgnoreCase(accessType);
            writeFields = isRead ? List.of() : nullSafeFields(request.writeFields());
            readFields = nullSafeFields(request.readFields());
            writeValueOptions = request.writeValueOptions() == null ? List.of() : request.writeValueOptions();
        }
        List<ValueOption> readValueOptions = request.readValueOptions() == null
                ? List.of()
                : request.readValueOptions();
        if (openLockedEmpty) {
            readValueOptions = List.of();
        } else if (descriptor.fixedFunctions() && template.isPresent()) {
            readValueOptions = (request.readValueOptions() == null || request.readValueOptions().isEmpty())
                    ? List.of()
                    : constrainFixedValueOptions(readValueOptions, template.get());
        }
        return new FunctionOptionPlan(writeAccess, writeFields, readFields, writeValueOptions, readValueOptions);
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

    static boolean isOpenLockedEmptyTemplate(CapabilityDescriptor descriptor, Optional<FunctionTemplate> template) {
        return !descriptor.fixedFunctions()
                && template.isPresent()
                && template.get().parameters().isEmpty();
    }

    static void rejectOpenLockedStructureMutation(ProductFunctionWriteRequest request) {
        if (request.properties() != null && !request.properties().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 properties");
        }
        if (request.writeFields() != null && !request.writeFields().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 writeFields");
        }
        if (request.readFields() != null && !request.readFields().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 readFields");
        }
        if (request.writeValueOptions() != null && !request.writeValueOptions().isEmpty()) {
            throw new IllegalArgumentException("能力预置无参功能不允许配置 writeValueOptions");
        }
    }

    static List<WriteFieldOption> nullSafeFields(List<WriteFieldOption> fields) {
        return fields == null ? List.of() : fields;
    }

    static ValueAccessType resolveWriteAccess(ProductFunctionWriteRequest request) {
        if (request.payloadMode() != null && !request.payloadMode().isBlank()) {
            return PayloadMode.from(request.payloadMode()) == PayloadMode.VALUE
                    ? ValueAccessType.VALUE
                    : ValueAccessType.STRUCT;
        }
        if (request.writeAccessType() != null && !request.writeAccessType().isBlank()) {
            return ValueAccessType.from(request.writeAccessType());
        }
        return ValueAccessType.STRUCT;
    }

    static List<PropertyItem> resolveFunctionProperties(
            ProductFunctionWriteRequest request, Optional<FunctionTemplate> template) {
        if (request.properties() != null) {
            return request.properties();
        }
        return template.map(item -> PropertySchemas.toPropertyItems(item.parameters())).orElse(List.of());
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
            fields.add(new WriteFieldOption(
                    field.name(),
                    field.description(),
                    field.type().code(),
                    field.type().code(),
                    false,
                    PropertySchemas.choicesToValueOptions(field),
                    field.format().code()));
        }
        return List.copyOf(fields);
    }

    static FunctionTemplate requireContractTemplate(CapabilityDescriptor descriptor, String accessType) {
        return descriptor.functionTemplateByAccessType(accessType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "能力未定义 " + accessType + " 参数契约: " + descriptor.capabilityType()));
    }

    static List<WriteFieldOption> bindContractFields(
            FunctionTemplate template,
            List<WriteFieldOption> requested,
            PayloadMode mode) {
        List<WriteFieldOption> seeded = seedContractFields(template, mode);
        if (requested == null || requested.isEmpty()) {
            return seeded;
        }
        Map<String, WriteFieldOption> byField = new LinkedHashMap<>();
        for (WriteFieldOption field : requested) {
            byField.put(field.field(), field);
        }
        List<WriteFieldOption> result = new ArrayList<>();
        for (WriteFieldOption seed : seeded) {
            WriteFieldOption req = byField.remove(seed.field());
            if (req == null) {
                result.add(seed);
                continue;
            }
            FieldSource source = FieldSource.from(req.source());
            if (source == FieldSource.MAPPED && !"value".equals(seed.field())) {
                throw new IllegalArgumentException("契约字段 " + seed.field() + " 不允许 mapped，仅 value 可映射");
            }
            String constant = req.constant();
            if (source == FieldSource.CONSTANT && (constant == null || constant.isBlank())) {
                constant = seed.constant();
            }
            List<ValueOption> options = seed.options();
            if ("value".equals(seed.field()) && mode != PayloadMode.VALUE
                    && req.options() != null && !req.options().isEmpty()) {
                options = constrainContractValueOptions(req.options(), template, mode);
            } else if (!"value".equals(seed.field())
                    && req.options() != null && !req.options().isEmpty() && !seed.options().isEmpty()) {
                options = mergeFixedFieldOptions(req.options(), seed.options());
            }
            result.add(new WriteFieldOption(
                    seed.field(),
                    req.description() != null && !req.description().isBlank() ? req.description() : seed.description(),
                    seed.accessDataType(),
                    seed.transformDataType(),
                    source != FieldSource.CALLER,
                    options,
                    seed.format(),
                    req.valueGenerator(),
                    source.wire(),
                    constant,
                    source == FieldSource.MAPPED
                            ? (req.callerField() == null || req.callerField().isBlank() ? "value" : req.callerField())
                            : req.callerField()));
        }
        if (!byField.isEmpty()) {
            throw new IllegalArgumentException(
                    "CONTRACT 功能不允许自定义字段: " + byField.keySet() + "（功能 " + template.functionId() + "）");
        }
        return List.copyOf(result);
    }

    static List<WriteFieldOption> seedContractFields(FunctionTemplate template, PayloadMode mode) {
        List<WriteFieldOption> fields = new ArrayList<>();
        for (SchemaField field : template.parameters()) {
            boolean valueField = "value".equals(field.name());
            boolean valueMode = mode == PayloadMode.VALUE;
            FieldSource source = valueField
                    ? (valueMode ? FieldSource.MAPPED : FieldSource.CALLER)
                    : FieldSource.CONSTANT;
            String constant = valueField || field.defaultValue() == null
                    ? null
                    : String.valueOf(field.defaultValue());
            fields.add(new WriteFieldOption(
                    field.name(),
                    field.description(),
                    field.type().code(),
                    field.type().code(),
                    source != FieldSource.CALLER,
                    PropertySchemas.choicesToValueOptions(field),
                    field.format().code(),
                    null,
                    source.wire(),
                    constant,
                    source == FieldSource.MAPPED ? "value" : null));
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
            result.add(new WriteFieldOption(
                    schemaField.name(),
                    description,
                    schemaField.type().code(),
                    schemaField.type().code(),
                    req != null && req.ignoreRequest(),
                    options,
                    req != null && req.format() != null && !req.format().isBlank()
                            ? req.format()
                            : schemaField.format().code(),
                    req != null ? req.valueGenerator() : null));
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
        List<ValueOption> allowed = flattenChoiceOptions(byField);
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
                .map(v -> v.optionValue())
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
                .map(v -> v.optionValue())
                .collect(Collectors.toSet());
        for (ValueOption option : allowed) {
            if (!submitted.contains(option.optionValue())) {
                result.add(option);
            }
        }
        return List.copyOf(result);
    }

    static List<ValueOption> constrainContractValueOptions(
            List<ValueOption> requested, FunctionTemplate template, PayloadMode mode) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        SchemaField valueField = null;
        if (template != null && template.parameters() != null) {
            for (SchemaField field : template.parameters()) {
                if ("value".equals(field.name())) {
                    valueField = field;
                    break;
                }
            }
        }
        List<String> choices = valueField == null || valueField.choices() == null
                ? List.of()
                : valueField.choices();
        List<ValueOption> result = new ArrayList<>();
        for (ValueOption option : requested) {
            if (option.optionValue() == null || option.optionValue().isBlank()) {
                throw new IllegalArgumentException("CONTRACT 值选项 optionValue 不能为空");
            }
            if (mode == PayloadMode.VALUE
                    && (option.mappingValue() == null || option.mappingValue().isBlank())) {
                throw new IllegalArgumentException(
                        "CONTRACT VALUE 映射 mappingValue 不能为空: " + option.optionValue());
            }
            if (!choices.isEmpty() && !choices.contains(option.optionValue())) {
                throw new IllegalArgumentException(
                        "CONTRACT 值选项取值非法: " + option.optionValue() + "，允许: " + choices);
            }
            result.add(option);
        }
        return List.copyOf(result);
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

    static String resolvePayloadMode(
            com.mtfm.gateway.catalog.entity.ProductFunctionEntity function, List<ValueOption> writeOptions) {
        if (function.getPayloadMode() != null && !function.getPayloadMode().isBlank()) {
            return function.getPayloadMode();
        }
        if (writeOptions != null && !writeOptions.isEmpty()
                && "VALUE".equalsIgnoreCase(function.getWriteAccessType())) {
            return PayloadMode.VALUE.wire();
        }
        return PayloadMode.STRUCT.wire();
    }
}
