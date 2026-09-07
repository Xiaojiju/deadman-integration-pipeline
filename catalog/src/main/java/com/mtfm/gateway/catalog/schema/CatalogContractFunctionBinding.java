package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.payload.FieldSource;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTRACT 能力：参数名由契约锁死，来源/常量/映射可配。
 */
final class CatalogContractFunctionBinding {

    private CatalogContractFunctionBinding() {
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
                options = CatalogFixedFunctionBinding.mergeFixedFieldOptions(req.options(), seed.options());
            }
            result.add(WriteFieldOption.builder(seed.field())
                    .description(req.description() != null && !req.description().isBlank()
                            ? req.description() : seed.description())
                    .accessDataType(seed.accessDataType())
                    .transformDataType(seed.transformDataType())
                    .ignoreRequest(source != FieldSource.CALLER)
                    .options(options)
                    .format(seed.format())
                    .valueGenerator(req.valueGenerator())
                    .source(source.wire())
                    .constant(constant)
                    .callerField(source == FieldSource.MAPPED
                            ? (req.callerField() == null || req.callerField().isBlank() ? "value" : req.callerField())
                            : req.callerField())
                    .build());
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
            fields.add(WriteFieldOption.builder(field.name())
                    .description(field.description())
                    .accessDataType(field.type().code())
                    .transformDataType(field.type().code())
                    .ignoreRequest(source != FieldSource.CALLER)
                    .options(PropertySchemas.choicesToValueOptions(field))
                    .format(field.format().code())
                    .source(source.wire())
                    .constant(constant)
                    .callerField(source == FieldSource.MAPPED ? "value" : null)
                    .build());
        }
        return List.copyOf(fields);
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
}
