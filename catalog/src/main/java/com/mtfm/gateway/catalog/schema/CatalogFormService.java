package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceEndpointView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceView;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.ProductFunctionView;
import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.dto.SupportedSchemaView;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaForms;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 能力 schema 与表单装配：CapabilityDescriptor → /supported → PropertyItem 落库 → 运行时投影。
 */
@Service
public class CatalogFormService {

    private final CatalogStore store;
    private final CapabilityRegistrar registrar;

    public CatalogFormService(CatalogStore store, @Autowired(required = false) CapabilityRegistrar registrar) {
        this.store = store;
        this.registrar = registrar;
    }

    public List<CapabilityDescriptor> listCapabilities() {
        return registrar == null ? List.of() : registrar.list();
    }

    public CapabilityDescriptor requireCapability(String capabilityType) {
        if (registrar == null) {
            throw new IllegalArgumentException("能力注册中心尚未装配");
        }
        return registrar.find(capabilityType)
                .orElseThrow(() -> new IllegalArgumentException("未登记能力: " + capabilityType));
    }

    public List<FunctionTemplate> capabilityFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates();
    }

    // ——— /supported ———

    public SupportedSchemaView supportedConnection(String capabilityType) {
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        List<SchemaField> schema = descriptor.connectionSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    public SupportedSchemaView supportedAddress(String capabilityType) {
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        List<SchemaField> schema = descriptor.addressSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    public List<SupportedFunctionView> supportedFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates().stream()
                .map(this::toSupportedFunction)
                .toList();
    }

    public SupportedFunctionView supportedFunction(String capabilityType, String functionId) {
        FunctionTemplate template = requireCapability(capabilityType).functionTemplate(functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能模板不存在: " + functionId));
        return toSupportedFunction(template);
    }

    private SupportedFunctionView toSupportedFunction(FunctionTemplate template) {
        List<SchemaField> schema = template.parameters();
        List<PropertyItem> properties = PropertySchemas.toPropertyItems(schema);
        Map<String, List<ValueOption>> choices = PropertySchemas.choiceOptionsByField(schema);
        List<ValueOption> writeOptions = flattenChoiceOptions(choices);
        return new SupportedFunctionView(
                template.functionId(),
                template.description(),
                template.accessType(),
                template.accessPermission(),
                ValueAccessType.VALUE.name(),
                SchemaForms.bind(schema, Map.of()),
                properties,
                writeOptions,
                choices);
    }

    private static List<ValueOption> flattenChoiceOptions(Map<String, List<ValueOption>> choices) {
        if (choices == null || choices.isEmpty()) {
            return List.of();
        }
        // VALUE 模式：若仅一个字段有 choices，直接作为 writeValueOptions
        if (choices.size() == 1) {
            return choices.values().iterator().next();
        }
        List<ValueOption> all = new ArrayList<>();
        choices.values().forEach(all::addAll);
        return List.copyOf(all);
    }

    private static List<ValueOption> flattenWriteFieldOptions(List<WriteFieldOption> writeFields) {
        if (writeFields == null || writeFields.isEmpty()) {
            return List.of();
        }
        List<WriteFieldOption> withOptions = writeFields.stream()
                .filter(field -> field.options() != null && !field.options().isEmpty())
                .toList();
        if (withOptions.isEmpty()) {
            return List.of();
        }
        if (withOptions.size() == 1) {
            return withOptions.get(0).options();
        }
        List<ValueOption> all = new ArrayList<>();
        for (WriteFieldOption field : withOptions) {
            all.addAll(field.options());
        }
        return List.copyOf(all);
    }

    // ——— Channel ———

    public ChannelEntity createChannel(ChannelWriteRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("通道 code 不能为空");
        }
        if (request.capabilityType() == null || request.capabilityType().isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
        if (store.findChannel(request.code()).isPresent()) {
            throw new IllegalArgumentException("通道编码已存在: " + request.code());
        }
        CapabilityDescriptor descriptor = requireCapability(request.capabilityType());
        List<PropertyItem> items = resolveProperties(request.properties(), request.connection());
        SchemaValidator.require(descriptor.connectionSchema(), PropertySchemas.toValueMap(items), "通道 connection");
        ChannelEntity entity = new ChannelEntity();
        entity.setCode(request.code());
        entity.setCapabilityType(request.capabilityType());
        entity.setConnection(JsonMaps.write(PropertySchemas.toValueMap(items)));
        entity.setEnabled(request.enabled());
        ChannelEntity saved = store.saveChannel(entity);
        store.properties().replaceChannelProperties(saved.getId(), items);
        return saved;
    }

    public ChannelEntity updateChannel(String channelIdOrCode, ChannelWriteRequest request) {
        ChannelEntity entity = store.findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        CapabilityDescriptor descriptor = requireCapability(entity.getCapabilityType());
        if (request != null) {
            if (request.properties() != null || request.connection() != null) {
                List<PropertyItem> items = resolveProperties(request.properties(), request.connection());
                SchemaValidator.require(descriptor.connectionSchema(), PropertySchemas.toValueMap(items), "通道 connection");
                entity.setConnection(JsonMaps.write(PropertySchemas.toValueMap(items)));
                store.properties().replaceChannelProperties(entity.getId(), items);
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        }
        return store.updateChannel(entity);
    }

    // ——— Product ———

    public ProductEntity createProduct(ProductWriteRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("产品 code 不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("产品 name 不能为空");
        }
        if (store.listProducts().stream().anyMatch(item -> request.code().equals(item.getCode()))) {
            throw new IllegalArgumentException("产品编码已存在: " + request.code());
        }
        ProductEntity entity = new ProductEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        ProductEntity saved = store.saveProduct(entity);
        if (request.seedCapabilityType() != null && !request.seedCapabilityType().isBlank()) {
            importCapabilityFunctions(saved.getId(), request.seedCapabilityType());
        }
        return saved;
    }

    public ProductEntity updateProduct(String productId, ProductWriteRequest request) {
        ProductEntity entity = store.findProduct(productId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        if (request != null) {
            if (request.name() != null && !request.name().isBlank()) {
                entity.setName(request.name());
            }
            if (request.description() != null) {
                entity.setDescription(request.description());
            }
        }
        return store.updateProduct(entity);
    }

    public List<ProductFunctionEntity> importCapabilityFunctions(String productId, String capabilityType) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        if (descriptor.functionTemplates().isEmpty()) {
            throw new IllegalArgumentException("能力无预置功能模板: " + capabilityType);
        }
        return descriptor.functionTemplates().stream()
                .filter(template -> store.findFunction(productId, template.functionId()).isEmpty())
                .map(template -> createFunctionFromTemplate(productId, capabilityType, template))
                .toList();
    }

    private ProductFunctionEntity createFunctionFromTemplate(
            String productId, String capabilityType, FunctionTemplate template) {
        List<PropertyItem> properties = PropertySchemas.toPropertyItems(template.parameters());
        List<WriteFieldOption> writeFields = templateWriteFields(template);
        return createFunction(productId, new ProductFunctionWriteRequest(
                template.functionId(),
                template.accessType(),
                template.accessPermission(),
                capabilityType,
                ValueAccessType.STRUCT.name(),
                properties,
                null,
                null,
                writeFields,
                null,
                null,
                0,
                template.description()));
    }

    public ProductFunctionEntity createFunction(String productId, ProductFunctionWriteRequest request) {
        if (request == null || request.functionId() == null || request.functionId().isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        if (request.capabilityType() == null || request.capabilityType().isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
        if (store.findFunction(productId, request.functionId()).isPresent()) {
            throw new IllegalArgumentException("产品功能已存在: " + productId + "/" + request.functionId());
        }
        CapabilityDescriptor descriptor = requireCapability(request.capabilityType());
        Optional<FunctionTemplate> template = descriptor.functionTemplate(request.functionId());
        if (descriptor.fixedFunctions() && template.isEmpty()) {
            throw new IllegalArgumentException(
                    "功能不在能力预置列表中: " + request.functionId() + "（" + request.capabilityType() + "）");
        }
        if (!descriptor.fixedFunctions() && template.isEmpty()
                && (request.accessType() == null || request.accessType().isBlank())) {
            throw new IllegalArgumentException("OPEN 能力自定义功能须指定 accessType（READ/WRITE）");
        }
        List<PropertyItem> items = resolveFunctionProperties(request, template);
        if (descriptor.fixedFunctions() && template.isPresent()) {
            items = constrainFixedProperties(items, template.get());
        }
        ValueAccessType writeAccess;
        List<ValueOption> writeValueOptions;
        List<WriteFieldOption> writeFields;
        if (descriptor.fixedFunctions()) {
            // FIXED：参数字段以 writeFields 罗列（field 固定，可改说明）；下发枚举挂在对应 field.options
            writeAccess = ValueAccessType.STRUCT;
            writeFields = resolveFixedWriteFields(request.writeFields(), template);
            writeValueOptions = List.of();
        } else {
            writeAccess = ValueAccessType.from(request.writeAccessType());
            writeValueOptions = resolveWriteValueOptions(request, template);
            writeFields = request.writeFields() == null ? List.of() : request.writeFields();
        }
        List<ValueOption> readValueOptions = request.readValueOptions() == null
                ? List.of()
                : request.readValueOptions();
        if (descriptor.fixedFunctions() && template.isPresent()) {
            // 空列表表示无读选项，不要回落成模板全量 choices
            readValueOptions = (request.readValueOptions() == null || request.readValueOptions().isEmpty())
                    ? List.of()
                    : constrainFixedValueOptions(readValueOptions, template.get());
        }

        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setProductId(productId);
        entity.setFunctionId(request.functionId());
        entity.setAccessType(request.accessType() != null
                ? request.accessType()
                : template.map(t -> t.accessType()).orElse("WRITE"));
        entity.setAccessPermission(request.accessPermission() != null
                ? request.accessPermission()
                : template.map(t -> t.accessPermission()).orElse(2));
        entity.setCapabilityType(request.capabilityType());
        entity.setOptionSchema(JsonMaps.write(PropertySchemas.toValueMap(items)));
        entity.setProtocolMapping(descriptor.fixedFunctions() || request.protocolMapping() == null
                ? null
                : JsonMaps.write(request.protocolMapping()));
        entity.setSortIndex(request.sortIndex());
        entity.setWriteAccessType(writeAccess.name());
        entity.setDescription(request.description() != null
                ? request.description()
                : template.map(t -> t.description()).orElse(null));
        ProductFunctionEntity saved = store.saveFunction(entity);
        store.properties().replaceFunctionProperties(saved.getId(), items);
        store.properties().replaceWriteOptions(saved.getId(), writeAccess, writeValueOptions, writeFields);
        store.properties().replaceReadValueOptions(saved.getId(), readValueOptions);
        return saved;
    }

    public ProductFunctionEntity updateFunction(String productId, String functionId,
            ProductFunctionWriteRequest request) {
        ProductFunctionEntity entity = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        if (request == null) {
            return entity;
        }
        CapabilityDescriptor descriptor = requireCapability(entity.getCapabilityType());
        Optional<FunctionTemplate> template = descriptor.functionTemplate(functionId);
        if (request.properties() != null || request.parameters() != null) {
            List<PropertyItem> items = resolveProperties(request.properties(), request.parameters());
            if (descriptor.fixedFunctions() && template.isPresent()) {
                items = constrainFixedProperties(items, template.get());
            }
            entity.setOptionSchema(JsonMaps.write(PropertySchemas.toValueMap(items)));
            store.properties().replaceFunctionProperties(entity.getId(), items);
        }
        if (request.accessType() != null && !request.accessType().isBlank()) {
            if (descriptor.fixedFunctions() && template.isPresent()
                    && !template.get().accessType().equalsIgnoreCase(request.accessType())) {
                throw new IllegalArgumentException("FIXED 功能不允许修改 accessType");
            }
            entity.setAccessType(request.accessType());
        }
        if (request.accessPermission() != null) {
            entity.setAccessPermission(request.accessPermission());
        }
        if (request.protocolMapping() != null) {
            if (descriptor.fixedFunctions()) {
                throw new IllegalArgumentException("FIXED 功能不支持自定义 protocolMapping");
            }
            entity.setProtocolMapping(JsonMaps.write(request.protocolMapping()));
        }
        if (request.sortIndex() != null) {
            entity.setSortIndex(request.sortIndex());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.writeAccessType() != null
                || request.writeValueOptions() != null
                || request.writeFields() != null) {
            ValueAccessType writeAccess;
            List<ValueOption> writeOpts;
            List<WriteFieldOption> fields;
            if (descriptor.fixedFunctions()) {
                writeAccess = ValueAccessType.STRUCT;
                fields = resolveFixedWriteFields(request.writeFields(), template);
                writeOpts = List.of();
            } else {
                writeAccess = request.writeAccessType() != null
                        ? ValueAccessType.from(request.writeAccessType())
                        : ValueAccessType.from(entity.getWriteAccessType());
                writeOpts = request.writeValueOptions();
                fields = request.writeFields();
            }
            entity.setWriteAccessType(writeAccess.name());
            store.properties().replaceWriteOptions(entity.getId(), writeAccess, writeOpts, fields);
        }
        if (request.readValueOptions() != null) {
            List<ValueOption> readOpts = request.readValueOptions();
            if (descriptor.fixedFunctions() && template.isPresent()) {
                readOpts = readOpts.isEmpty()
                        ? List.of()
                        : constrainFixedValueOptions(readOpts, template.get());
            }
            store.properties().replaceReadValueOptions(entity.getId(), readOpts);
        }
        return store.updateFunction(entity);
    }

    private List<PropertyItem> resolveFunctionProperties(
            ProductFunctionWriteRequest request, Optional<FunctionTemplate> template) {
        if (request.properties() != null) {
            return request.properties();
        }
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            return PropertySchemas.fromValueMap(request.parameters());
        }
        return template.map(item -> PropertySchemas.toPropertyItems(item.parameters())).orElse(List.of());
    }

    private List<ValueOption> resolveWriteValueOptions(
            ProductFunctionWriteRequest request, Optional<FunctionTemplate> template) {
        if (request.writeValueOptions() != null) {
            return request.writeValueOptions();
        }
        return template
                .map(item -> flattenChoiceOptions(PropertySchemas.choiceOptionsByField(item.parameters())))
                .orElse(List.of());
    }

    /**
     * FIXED：属性名必须落在模板 parameters 内；带 choices 的字段取值必须是规定枚举。
     */
    private static List<PropertyItem> constrainFixedProperties(
            List<PropertyItem> items, FunctionTemplate template) {
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
            // 保留调用方自定义说明；取值/类型仍受模板约束
            String description = item.description() == null || item.description().isBlank()
                    ? field.description()
                    : item.description();
            result.add(new PropertyItem(
                    field.name(),
                    item.attributeValue(),
                    field.type(),
                    description));
        }
        return List.copyOf(result);
    }

    /** 模板 parameters → STRUCT 写字段（含 choices 子选项）。 */
    private static List<WriteFieldOption> templateWriteFields(FunctionTemplate template) {
        if (template == null || template.parameters() == null || template.parameters().isEmpty()) {
            return List.of();
        }
        List<WriteFieldOption> fields = new ArrayList<>();
        for (SchemaField field : template.parameters()) {
            fields.add(new WriteFieldOption(
                    field.name(),
                    field.description(),
                    field.type(),
                    field.type(),
                    false,
                    PropertySchemas.choicesToValueOptions(field)));
        }
        return List.copyOf(fields);
    }

    private List<WriteFieldOption> resolveFixedWriteFields(
            List<WriteFieldOption> requested, Optional<FunctionTemplate> template) {
        if (template.isEmpty()) {
            return List.of();
        }
        if (requested == null || requested.isEmpty()) {
            return templateWriteFields(template.get());
        }
        return constrainFixedWriteFields(requested, template.get());
    }

    /**
     * FIXED：writeFields 的 field 必须 ⊆ 模板 parameters；options 取值 ⊆ choices；可改 description。
     */
    private static List<WriteFieldOption> constrainFixedWriteFields(
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
                    schemaField.type(),
                    schemaField.type(),
                    req != null && req.ignoreRequest(),
                    options));
        }
        if (!byField.isEmpty()) {
            throw new IllegalArgumentException(
                    "FIXED 功能不允许自定义写字段: " + byField.keySet() + "（功能 " + template.functionId() + "）");
        }
        return List.copyOf(result);
    }

    private static List<ValueOption> mergeFixedFieldOptions(
            List<ValueOption> requested, List<ValueOption> allowed) {
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

    /**
     * FIXED：读写选项取值必须 ⊆ 模板所有 choices；空入参则回落为模板全量 choices。
     */
    private static List<ValueOption> constrainFixedValueOptions(
            List<ValueOption> requested, FunctionTemplate template) {
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
        java.util.Set<String> allowedValues = allowed.stream()
                .map(option -> option.optionValue())
                .collect(java.util.stream.Collectors.toSet());
        List<ValueOption> result = new ArrayList<>();
        for (ValueOption option : requested) {
            if (!allowedValues.contains(option.optionValue())) {
                throw new IllegalArgumentException(
                        "FIXED 功能选项取值非法: " + option.optionValue() + "，允许: " + allowedValues);
            }
            result.add(option);
        }
        java.util.Set<String> submitted = result.stream()
                .map(option -> option.optionValue())
                .collect(java.util.stream.Collectors.toSet());
        for (ValueOption option : allowed) {
            if (!submitted.contains(option.optionValue())) {
                result.add(option);
            }
        }
        return List.copyOf(result);
    }

    // ——— Device ———

    public DeviceEntity createDevice(DeviceWriteRequest request) {
        if (request == null || request.deviceCode() == null || request.deviceCode().isBlank()) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }
        if (request.productId() == null || request.productId().isBlank()) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        store.findProduct(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + request.productId()));
        if (store.findDeviceByCode(request.deviceCode()).isPresent()) {
            throw new IllegalArgumentException("设备编码已存在: " + request.deviceCode());
        }
        Map<String, List<PropertyItem>> overrides = resolveFunctionOverrides(
                request.functionOverrides(), request.optionOverrides());
        DeviceEntity entity = new DeviceEntity();
        entity.setDeviceCode(request.deviceCode());
        entity.setProductId(request.productId());
        entity.setName(request.name());
        entity.setOptionOverrides(JsonMaps.write(toLegacyOverrideMap(overrides)));
        entity.setEnabled(request.enabled());
        DeviceEntity saved = store.saveDevice(entity);
        store.properties().replaceAllDeviceOverrides(saved.getId(), overrides);
        return saved;
    }

    public DeviceEntity updateDevice(String deviceCode, DeviceUpdateRequest request) {
        DeviceEntity entity = requireDevice(deviceCode);
        if (request != null) {
            if (request.name() != null) {
                entity.setName(request.name());
            }
            if (request.functionOverrides() != null || request.optionOverrides() != null) {
                Map<String, List<PropertyItem>> overrides = resolveFunctionOverrides(
                        request.functionOverrides(), request.optionOverrides());
                entity.setOptionOverrides(JsonMaps.write(toLegacyOverrideMap(overrides)));
                store.properties().replaceAllDeviceOverrides(entity.getId(), overrides);
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        }
        return store.updateDevice(entity);
    }

    public DeviceEndpointEntity updateEndpoint(String endpointId, DeviceEndpointWriteRequest request) {
        DeviceEndpointEntity entity = store.findEndpoint(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("端点不存在: " + endpointId));
        ChannelEntity channel = store.findChannel(entity.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + entity.getChannelId()));
        if (request != null) {
            if (request.channelId() != null && !request.channelId().isBlank()
                    && !request.channelId().equals(channel.getId())
                    && !request.channelId().equals(channel.getCode())) {
                channel = store.findChannel(request.channelId())
                        .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + request.channelId()));
                entity.setChannelId(channel.getId());
            }
            if (request.properties() != null || request.address() != null) {
                List<PropertyItem> items = resolveProperties(request.properties(), request.address());
                CapabilityDescriptor descriptor = requireCapability(channel.getCapabilityType());
                SchemaValidator.require(descriptor.addressSchema(), PropertySchemas.toValueMap(items), "端点 address");
                entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
                store.properties().replaceEndpointProperties(entity.getId(), items);
            }
        }
        return store.updateEndpoint(entity);
    }

    public DeviceEntity registerDevice(DeviceRegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("登记请求不能为空");
        }
        DeviceEntity device = createDevice(new DeviceWriteRequest(
                request.deviceCode(),
                request.productId(),
                request.name(),
                request.functionOverrides(),
                request.optionOverrides(),
                request.enabled()));
        if (request.endpoints() != null) {
            for (DeviceEndpointWriteRequest endpoint : request.endpoints()) {
                createEndpoint(device.getDeviceCode(), endpoint);
            }
        }
        return device;
    }

    public DeviceEndpointEntity createEndpoint(String deviceCodeOrId, DeviceEndpointWriteRequest request) {
        if (request == null || request.channelId() == null || request.channelId().isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        DeviceEntity device = requireDevice(deviceCodeOrId);
        ChannelEntity channel = store.findChannel(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + request.channelId()));
        CapabilityDescriptor descriptor = requireCapability(channel.getCapabilityType());
        List<PropertyItem> items = resolveProperties(request.properties(), request.address());
        SchemaValidator.require(descriptor.addressSchema(), PropertySchemas.toValueMap(items), "端点 address");
        DeviceEndpointEntity entity = new DeviceEndpointEntity();
        entity.setDeviceId(device.getId());
        entity.setChannelId(channel.getId());
        entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
        DeviceEndpointEntity saved = store.saveEndpoint(entity);
        store.properties().replaceEndpointProperties(saved.getId(), items);
        return saved;
    }

    public List<DeviceEntity> listDevices() {
        return store.listDevices();
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size) {
        return store.pageDevices(page, size);
    }

    public DeviceEntity requireDevice(String deviceCodeOrId) {
        return store.resolveDevice(deviceCodeOrId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCodeOrId));
    }

    public FunctionCommand buildCommand(String deviceCode, DeviceCommandRequest request) {
        if (request == null || request.functionId() == null || request.functionId().isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        DeviceEntity device = requireDevice(deviceCode);
        ProductFunctionEntity function = store.findFunction(device.getProductId(), request.functionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "功能未配置到产品: " + device.getProductId() + "/" + request.functionId()));
        Map<String, Object> merged = new LinkedHashMap<>(
                PropertySchemas.toValueMap(store.loadFunctionProperties(function)));
        merged.putAll(PropertySchemas.toValueMap(store.loadDeviceOverrides(device, function.getFunctionId())));
        if (request.arguments() != null) {
            merged.putAll(request.arguments());
        }
        Optional<FunctionTemplate> template = findTemplate(function.getCapabilityType(), function.getFunctionId());
        template.ifPresent(item -> SchemaValidator.require(item.parameters(), merged, "指令参数"));
        return FunctionCommand.of(device.getDeviceCode(), function.getFunctionId(), merged);
    }

    public List<FunctionFormView> productFunctions(String productId) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        return store.listFunctions(productId).stream()
                .map(function -> toForm(function, Map.of()))
                .toList();
    }

    public List<ProductFunctionView> listProductFunctionViews(String productId) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        return store.listFunctions(productId).stream()
                .map(this::toProductFunctionView)
                .toList();
    }

    public FunctionFormView productFunction(String productId, String functionId) {
        ProductFunctionEntity function = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        return toForm(function, Map.of());
    }

    public List<FunctionFormView> deviceFunctions(String deviceCode) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        Map<String, List<PropertyItem>> overrides = store.loadAllDeviceOverrides(device);
        return store.listFunctions(device.getProductId()).stream()
                .map(function -> toForm(function, overrides))
                .toList();
    }

    public FunctionFormView deviceFunction(String deviceCode, String functionId) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return toForm(function, store.loadAllDeviceOverrides(device));
    }

    public ProductFunctionView toProductFunctionView(ProductFunctionEntity function) {
        List<PropertyItem> props = store.loadFunctionProperties(function);
        ValueAccessType writeAccess = ValueAccessType.from(function.getWriteAccessType());
        return new ProductFunctionView(
                function.getId(),
                function.getProductId(),
                function.getFunctionId(),
                resolveDescription(function),
                function.getAccessType(),
                function.getAccessPermission(),
                function.getCapabilityType(),
                writeAccess.name(),
                props,
                store.properties().listWriteValueOptions(function.getId()),
                store.properties().listWriteFields(function.getId()),
                store.properties().listReadValueOptions(function.getId()),
                JsonMaps.readMap(function.getProtocolMapping()),
                function.getSortIndex());
    }

    public ChannelView toChannelView(ChannelEntity entity) {
        return new ChannelView(
                entity.getId(),
                entity.getCode(),
                entity.getCapabilityType(),
                store.loadChannelProperties(entity),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public DeviceView toDeviceView(DeviceEntity entity) {
        return new DeviceView(
                entity.getId(),
                entity.getDeviceCode(),
                entity.getProductId(),
                entity.getName(),
                store.loadAllDeviceOverrides(entity),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public DeviceEndpointView toEndpointView(DeviceEndpointEntity entity) {
        return new DeviceEndpointView(
                entity.getId(),
                entity.getDeviceId(),
                entity.getChannelId(),
                store.loadEndpointProperties(entity),
                entity.getCreatedAt());
    }

    public PageResult<ChannelView> pageChannelViews(int page, int size) {
        PageResult<ChannelEntity> raw = store.pageChannels(page, size);
        return new PageResult<>(
                raw.items().stream().map(this::toChannelView).toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    public PageResult<DeviceView> pageDeviceViews(int page, int size) {
        PageResult<DeviceEntity> raw = store.pageDevices(page, size);
        return new PageResult<>(
                raw.items().stream().map(this::toDeviceView).toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    private FunctionFormView toForm(ProductFunctionEntity function, Map<String, List<PropertyItem>> deviceOverrides) {
        List<PropertyItem> base = store.loadFunctionProperties(function);
        List<PropertyItem> overrideItems = deviceOverrides.getOrDefault(function.getFunctionId(), List.of());
        Map<String, Object> values = new LinkedHashMap<>(PropertySchemas.toValueMap(base));
        values.putAll(PropertySchemas.toValueMap(overrideItems));
        List<SchemaField> schema = inferSchema(values);
        Optional<FunctionTemplate> template = findTemplate(function.getCapabilityType(), function.getFunctionId());
        if (template.isPresent()) {
            schema = template.get().parameters();
        }
        List<FormField> fields = SchemaForms.bind(schema, values);
        ValueAccessType writeAccess = ValueAccessType.from(function.getWriteAccessType());
        List<ValueOption> writeOptions = store.properties().listWriteValueOptions(function.getId());
        if (writeOptions.isEmpty()) {
            // STRUCT：把各 writeField.options 展平供指令下发按钮使用（保留自定义 description）
            writeOptions = flattenWriteFieldOptions(store.properties().listWriteFields(function.getId()));
        }
        if (writeOptions.isEmpty() && template.isPresent()) {
            writeOptions = flattenChoiceOptions(PropertySchemas.choiceOptionsByField(template.get().parameters()));
        }
        return new FunctionFormView(
                function.getFunctionId(),
                resolveDescription(function),
                function.getAccessType(),
                function.getAccessPermission() == null ? 2 : function.getAccessPermission(),
                function.getCapabilityType(),
                writeAccess.name(),
                fields,
                PropertySchemas.fromValueMap(values),
                writeOptions,
                values,
                JsonMaps.readMap(function.getProtocolMapping()));
    }

    private String resolveDescription(ProductFunctionEntity function) {
        if (function.getDescription() != null && !function.getDescription().isBlank()) {
            return function.getDescription();
        }
        return findTemplate(function.getCapabilityType(), function.getFunctionId())
                .map(t -> t.description())
                .filter(text -> text != null && !text.isBlank())
                .orElse(function.getFunctionId());
    }

    private Optional<FunctionTemplate> findTemplate(String capabilityType, String functionId) {
        if (capabilityType == null || capabilityType.isBlank() || registrar == null) {
            return Optional.empty();
        }
        return registrar.find(capabilityType).flatMap(descriptor -> descriptor.functionTemplate(functionId));
    }

    private static List<PropertyItem> resolveProperties(List<PropertyItem> properties, Map<String, Object> legacy) {
        if (properties != null) {
            return properties;
        }
        return PropertySchemas.fromValueMap(legacy == null ? Map.of() : legacy);
    }

    private static Map<String, List<PropertyItem>> resolveFunctionOverrides(
            Map<String, List<PropertyItem>> overrides, Map<String, Object> legacy) {
        if (overrides != null) {
            return overrides;
        }
        return CatalogStore.parseLegacyOverrides(legacy);
    }

    private static Map<String, Object> toLegacyOverrideMap(Map<String, List<PropertyItem>> overrides) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (overrides == null) {
            return result;
        }
        overrides.forEach((functionId, items) -> result.put(functionId, PropertySchemas.toValueMap(items)));
        return result;
    }

    private static List<SchemaField> inferSchema(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(entry -> SchemaField.optional(entry.getKey(), inferType(entry.getValue()), "", entry.getValue()))
                .toList();
    }

    private static String inferType(Object value) {
        if (value instanceof Number) {
            return "int";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return "string";
    }
}
