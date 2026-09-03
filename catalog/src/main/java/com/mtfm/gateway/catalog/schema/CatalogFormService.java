package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
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
import com.mtfm.gateway.catalog.store.FunctionOptionBundle;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaForms;
import com.mtfm.gateway.catalog.payload.PayloadDefinitionResolver;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.payload.TopicCatalog;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 能力 schema 与表单装配：CapabilityDescriptor → /supported → PropertyItem 落库 → 运行时投影。
 */
@Service
public class CatalogFormService {

    /** 通道视图脱敏占位；更新时回写原值，避免把掩码存进库。 */
    static final String SECRET_MASK = CatalogConnectionSupport.SECRET_MASK;

    private final CatalogStore store;
    private final CapabilityRegistrar registrar;
    private final CatalogFormViews views;

    public CatalogFormService(
            CatalogStore store,
            @Autowired(required = false) CapabilityRegistrar registrar,
            ObjectProvider<CatalogApplyService> applyService) {
        this.store = store;
        this.registrar = registrar;
        this.views = new CatalogFormViews(store, registrar, applyService);
    }

    /**
     * 列出所有能力
     * 这个方法主要是用于查询支持的「能力」，能力可能附带FIXED_FUNCTIONS、CONTRACTED_PARAMETERS等特性。
     * 
     * @return 能力列表
     */
    public List<CapabilityDescriptor> listCapabilities() {
        return registrar == null ? List.of() : registrar.list();
    }

    /**
     * 获取能力
     * 这个方法主要获取指定能力的描述，描述中附带了能力注册到总线中支持范围。
     * 
     * @see CapabilityDescriptor 能力描述符
     * @param capabilityType 能力类型
     * @return 能力描述符
     */
    public CapabilityDescriptor requireCapability(String capabilityType) {
        if (registrar == null) {
            throw new IllegalArgumentException("能力注册中心尚未装配");
        }
        return registrar.find(capabilityType)
                .orElseThrow(() -> new IllegalArgumentException("未登记能力: " + capabilityType));
    }

    /**
     * 获取能力的功能模板
     * 这个方式是获取指定能力所原生支持的功能模板列表，这些功能模板是能力在总线中支持范围。
     * 
     * @param capabilityType 能力类型
     * @return 功能模板列表
     */
    public List<FunctionTemplate> capabilityFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates();
    }

    // ——— /supported ———

    /**
     * 获取能力的连接参数模板
     * 这个方式是获取指定能力所原生支持的连接参数模板，这些连接参数模板是能力在总线中支持范围。
     * 
     * @param capabilityType 能力类型
     * @return 连接参数模板列表
     */
    public SupportedSchemaView supportedConnection(String capabilityType) {
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        List<SchemaField> schema = descriptor.connectionSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    /**
     * 获取能力的地址参数模板
     * 这个方式是获取指定能力所原生支持的地址参数模板，这些地址参数模板是能力在总线中支持范围。
     * 
     * @param capabilityType 能力类型
     * @return 地址参数模板列表
     */
    public SupportedSchemaView supportedAddress(String capabilityType) {
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        List<SchemaField> schema = descriptor.addressSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    /**
     * 获取能力的功能模板
     * 这个方式是获取指定能力所原生支持的功能模板列表，这些功能模板是能力在总线中支持范围。
     * 
     * @param capabilityType 能力类型
     * @return 功能模板列表
     */
    public List<SupportedFunctionView> supportedFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates().stream()
                .map(views::toSupportedFunction)
                .toList();
    }

    /**
     * 获取能力的功能模板
     * 这个方式是获取指定能力所原生支持的功能模板，这些功能模板是能力在总线中支持范围。
     * 
     * @param capabilityType 能力类型
     * @param functionId     功能ID
     * @return 功能模板
     * @throws IllegalArgumentException 如果功能模板不存在
     */
    public SupportedFunctionView supportedFunction(String capabilityType, String functionId) {
        FunctionTemplate template = requireCapability(capabilityType).functionTemplate(functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能模板不存在: " + functionId));
        return views.toSupportedFunction(template);
    }

    // ——— Channel ———

    /**
     * 创建通道
     * 这个方式是创建指定通道，通道包含了通道ID、通道编码、通道类型、通道连接、通道是否启用等。
     * 
     * @param request 通道写请求
     * @return 通道实体
     * @throws IllegalArgumentException 如果通道编码为空或通道类型为空或通道编码已存在
     */
    @Transactional
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
        // 获取能力描述符
        CapabilityDescriptor descriptor = requireCapability(request.capabilityType());
        List<PropertyItem> items = resolveProperties(request.properties(), request.connection());
        CatalogConnectionSupport.validateConnection(descriptor, items);
        ChannelEntity entity = new ChannelEntity();
        entity.setCode(request.code());
        entity.setCapabilityType(request.capabilityType());
        entity.setConnection(JsonMaps.write(PropertySchemas.toValueMap(items)));
        entity.setEnabled(request.enabled());
        ChannelEntity saved = store.saveChannel(entity);
        store.properties().replaceChannelProperties(saved.getId(), items);
        return saved;
    }

    /**
     * 更新通道
     * 这个方式是更新指定通道，通道包含了通道ID、通道编码、通道类型、通道连接、通道是否启用等。
     * 
     * @param channelIdOrCode 通道ID或编码
     * @param request         通道写请求
     * @return 通道实体
     * @throws IllegalArgumentException 如果通道不存在或通道编码为空或通道类型为空或通道编码已存在
     */
    @Transactional
    public ChannelEntity updateChannel(String channelIdOrCode, ChannelWriteRequest request) {
        ChannelEntity entity = store.findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        CapabilityDescriptor descriptor = requireCapability(entity.getCapabilityType());
        if (request != null) {
            if (request.properties() != null || request.connection() != null) {
                List<PropertyItem> items = CatalogConnectionSupport.restoreSecrets(
                        resolveProperties(request.properties(), request.connection()),
                        store.loadChannelProperties(entity),
                        descriptor.connectionSchema());
                CatalogConnectionSupport.validateConnection(descriptor, items);
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

    /**
     * 创建产品
     * 这个方式是创建指定产品，产品包含了产品ID、产品编码、产品名称、产品描述等。
     * 
     * @param request 产品写请求
     * @return 产品实体
     * @throws IllegalArgumentException 如果产品编码为空或产品名称为空或产品编码已存在
     */
    @Transactional
    public ProductEntity createProduct(ProductWriteRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("产品 code 不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("产品 name 不能为空");
        }
        if (store.findProductByCode(request.code()).isPresent()) {
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

    /**
     * 更新产品
     * 这个方式是更新指定产品，产品包含了产品ID、产品编码、产品名称、产品描述等。
     * 
     * @param productId 产品ID
     * @param request   产品写请求
     * @return 产品实体
     * @throws IllegalArgumentException 如果产品不存在或产品编码为空或产品名称已存在
     */
    @Transactional
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

    /**
     * 导入能力功能
     * 这个方式是导入指定能力的功能，功能包含了功能ID、功能类型、功能参数、功能写选项、功能读选项、功能值访问类型、功能值访问权限、功能值访问选项等。
     * 
     * @param productId      产品ID
     * @param capabilityType 能力类型
     * @return 产品功能实体列表
     * @throws IllegalArgumentException 如果产品不存在或能力无预置功能模板或产品功能已存在
     */
    @Transactional
    public List<ProductFunctionEntity> importCapabilityFunctions(String productId, String capabilityType) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        if (descriptor.functionTemplates().isEmpty()) {
            throw new IllegalArgumentException("能力无预置功能模板: " + capabilityType);
        }
        List<ProductFunctionEntity> imported = new ArrayList<>();
        for (FunctionTemplate template : descriptor.functionTemplates()) {
            Optional<ProductFunctionEntity> existing = store.findFunction(productId, template.functionId());
            if (existing.isEmpty()) {
                imported.add(createFunctionFromTemplate(productId, capabilityType, template));
            } else {
                imported.add(refreshFunctionFromTemplate(capabilityType, template, existing.get()));
            }
        }
        return List.copyOf(imported);
    }

    /**
     * 创建产品功能实体
     * 这个方式是创建指定产品功能实体，产品功能实体包含了功能ID、功能类型、功能参数、功能写选项、功能读选项、功能值访问类型、功能值访问权限、功能值访问选项等。
     * 
     * @param productId      产品ID
     * @param capabilityType 能力类型
     * @param template       功能模板
     * @return 产品功能实体
     * @throws IllegalArgumentException 如果产品不存在或能力类型为空或能力类型无预置功能模板或功能模板为空或功能模板无参数或功能模板无写选项或功能模板无读选项或功能模板无值访问类型或功能模板无值访问权限或功能模板无值访问选项
     */
    private ProductFunctionEntity createFunctionFromTemplate(
            String productId, String capabilityType, FunctionTemplate template) {
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        List<PropertyItem> properties = PropertySchemas.toPropertyItems(template.parameters());
        List<WriteFieldOption> writeFields = descriptor.contractedParameters()
                ? CatalogFunctionBinding.bindContractFields(template, null, PayloadMode.STRUCT)
                : CatalogFunctionBinding.templateWriteFields(template);
        boolean read = "READ".equalsIgnoreCase(template.accessType());
        return createFunction(productId, new ProductFunctionWriteRequest(
                template.functionId(),
                template.accessType(),
                template.accessPermission(),
                capabilityType,
                ValueAccessType.STRUCT.name(),
                properties,
                null,
                read ? null : writeFields,
                read ? writeFields : null,
                null,
                0,
                template.description(),
                null,
                null,
                null,
                null));
    }

    /**
     * 模板升级时按现有绑定重刷契约字段：保留用户 constant/mapping，补上模板新增参数，丢掉已删除参数。
     */
    private ProductFunctionEntity refreshFunctionFromTemplate(
            String capabilityType, FunctionTemplate template, ProductFunctionEntity entity) {
        if (entity.getCapabilityType() != null
                && !entity.getCapabilityType().equalsIgnoreCase(capabilityType)) {
            return entity;
        }
        CapabilityDescriptor descriptor = requireCapability(capabilityType);
        Optional<FunctionTemplate> locked = Optional.of(template);
        if (CatalogFunctionBinding.isOpenLockedEmptyTemplate(descriptor, locked)) {
            return entity;
        }
        PayloadMode mode = PayloadMode.from(entity.getPayloadMode());
        boolean read = "READ".equalsIgnoreCase(entity.getAccessType());
        if (descriptor.contractedParameters()) {
            FunctionTemplate contract = CatalogFunctionBinding.requireContractTemplate(descriptor,
                    entity.getAccessType());
            List<WriteFieldOption> existing = read
                    ? store.properties().listReadFields(entity.getId())
                    : store.properties().listWriteFields(entity.getId());
            List<WriteFieldOption> known = CatalogFunctionBinding.filterKnownFields(existing, contract);
            List<WriteFieldOption> bound = CatalogFunctionBinding.bindContractFields(contract,
                    known.isEmpty() ? null : known, mode);
            if (read) {
                store.properties().replaceReadFields(entity.getId(), bound);
            } else {
                store.properties().replaceWriteOptions(
                        entity.getId(),
                        ValueAccessType.from(entity.getWriteAccessType()),
                        store.properties().listWriteValueOptions(entity.getId()),
                        bound);
            }
        } else if (descriptor.fixedFunctions()) {
            List<WriteFieldOption> existing = store.properties().listWriteFields(entity.getId());
            List<WriteFieldOption> known = CatalogFunctionBinding.filterKnownFields(existing, template);
            List<WriteFieldOption> bound = CatalogFunctionBinding.constrainFixedWriteFields(
                    known.isEmpty() ? CatalogFunctionBinding.templateWriteFields(template) : known, template);
            store.properties().replaceWriteOptions(entity.getId(), ValueAccessType.STRUCT, List.of(), bound);
        }
        return entity;
    }

    /**
     * 创建产品功能
     * 这个方式是创建指定产品功能，产品功能包含了功能ID、功能类型、功能参数、功能写选项、功能读选项、功能值访问类型、功能值访问权限、功能值访问选项等。
     * 
     * @param productId 产品ID
     * @param request   产品功能写请求
     * @return 产品功能实体
     * @throws IllegalArgumentException 如果产品功能ID为空或产品功能类型为空或产品功能已存在
     */
    @Transactional
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
            throw new IllegalArgumentException("自定义功能须指定 accessType（READ/WRITE）");
        }
        boolean openLockedEmpty = CatalogFunctionBinding.isOpenLockedEmptyTemplate(descriptor, template);
        if (openLockedEmpty) {
            CatalogFunctionBinding.rejectOpenLockedStructureMutation(request);
        }
        List<PropertyItem> items = descriptor.fixedFunctions()
                ? CatalogFunctionBinding.resolveFunctionProperties(request, template)
                : List.of();
        if (descriptor.fixedFunctions() && template.isPresent()) {
            items = CatalogFunctionBinding.constrainFixedProperties(items, template.get());
        }
        if (openLockedEmpty) {
            items = List.of();
        }
        ValueAccessType writeAccess;
        List<ValueOption> writeValueOptions;
        List<WriteFieldOption> writeFields;
        List<WriteFieldOption> readFields;
        if (descriptor.fixedFunctions()) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = CatalogFunctionBinding.resolveFixedWriteFields(request.writeFields(), template);
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (openLockedEmpty) {
            writeAccess = ValueAccessType.STRUCT;
            writeFields = List.of();
            writeValueOptions = List.of();
            readFields = List.of();
        } else if (descriptor.contractedParameters()) {
            writeAccess = CatalogFunctionBinding.resolveWriteAccess(request);
            String access = request.accessType() != null && !request.accessType().isBlank()
                    ? request.accessType()
                    : template.map(t -> t.accessType()).orElse("WRITE");
            FunctionTemplate contract = CatalogFunctionBinding.requireContractTemplate(descriptor, access);
            PayloadMode mode = writeAccess == ValueAccessType.VALUE ? PayloadMode.VALUE : PayloadMode.STRUCT;
            boolean isRead = "READ".equalsIgnoreCase(access);
            writeFields = isRead ? List.of()
                    : CatalogFunctionBinding.bindContractFields(contract, request.writeFields(), mode);
            readFields = isRead ? CatalogFunctionBinding.bindContractFields(contract, request.readFields(), mode)
                    : List.of();
            writeValueOptions = CatalogFunctionBinding.constrainContractValueOptions(
                    request.writeValueOptions() == null ? List.of() : request.writeValueOptions(),
                    contract,
                    mode);
        } else {
            writeAccess = CatalogFunctionBinding.resolveWriteAccess(request);
            boolean isRead = "READ".equalsIgnoreCase(request.accessType());
            writeFields = isRead ? List.of() : CatalogFunctionBinding.nullSafeFields(request.writeFields());
            readFields = isRead ? CatalogFunctionBinding.nullSafeFields(request.readFields()) : List.of();
            writeValueOptions = request.writeValueOptions() == null ? List.of() : request.writeValueOptions();
        }
        List<ValueOption> readValueOptions = request.readValueOptions() == null
                ? List.of()
                : request.readValueOptions();
        if (openLockedEmpty) {
            readValueOptions = List.of();
        } else if (descriptor.fixedFunctions() && template.isPresent()) {
            // 空列表表示无读选项，不要回落成模板全量 choices
            readValueOptions = (request.readValueOptions() == null || request.readValueOptions().isEmpty())
                    ? List.of()
                    : CatalogFunctionBinding.constrainFixedValueOptions(readValueOptions, template.get());
        }

        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setProductId(productId);
        entity.setFunctionId(request.functionId());
        entity.setAccessType(openLockedEmpty
                ? template.map(t -> t.accessType()).orElse("WRITE")
                : request.accessType() != null
                        ? request.accessType()
                        : template.map(t -> t.accessType()).orElse("WRITE"));
        entity.setAccessPermission(request.accessPermission() != null
                ? request.accessPermission()
                : template.map(ft -> ft.accessPermission())
                        .orElseGet(() -> "READ".equalsIgnoreCase(entity.getAccessType())
                                ? AccessPermission.READ.code()
                                : AccessPermission.WRITE.code()));
        entity.setCapabilityType(request.capabilityType());
        entity.setOptionSchema(JsonMaps.write(PropertySchemas.toValueMap(items)));
        entity.setProtocolMapping(null);
        entity.setSortIndex(request.sortIndex());
        entity.setWriteAccessType(writeAccess.name());
        entity.setDescription(request.description() != null
                ? request.description()
                : template.map(t -> t.description()).orElse(null));
        PayloadDefinitionResolver.syncFromLegacyFields(entity, writeFields, readFields, writeValueOptions);
        if (PayloadDefinitionResolver.hasDirectPayload(request)) {
            PayloadDefinitionResolver.applyPayloadFromRequest(entity, request);
        }
        if (request.publishTopicSlot() != null) {
            entity.setPublishTopicSlot(request.publishTopicSlot());
        }
        if (request.subscribeTopicSlot() != null) {
            entity.setSubscribeTopicSlot(request.subscribeTopicSlot());
        }
        entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
        ProductFunctionEntity saved = store.saveFunction(entity);
        store.properties().replaceFunctionProperties(saved.getId(), items);
        store.properties().replaceWriteOptions(saved.getId(), writeAccess, writeValueOptions, writeFields);
        store.properties().replaceReadFields(saved.getId(), readFields);
        store.properties().replaceReadValueOptions(saved.getId(), readValueOptions);
        return saved;
    }

    /**
     * 更新产品功能
     * 这个方式是更新指定产品功能，产品功能包含了功能ID、功能类型、功能参数、功能写选项、功能读选项、功能值访问类型、功能值访问权限、功能值访问选项等。
     * 
     * @param productId  产品ID
     * @param functionId 功能ID
     * @param request    产品功能写请求
     * @return 产品功能实体
     * @throws IllegalArgumentException 如果产品功能不存在或产品功能ID为空或产品功能类型为空或产品功能已存在
     */
    @Transactional
    public ProductFunctionEntity updateFunction(String productId, String functionId,
            ProductFunctionWriteRequest request) {
        ProductFunctionEntity entity = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        if (request == null) {
            return entity;
        }
        CapabilityDescriptor descriptor = requireCapability(entity.getCapabilityType());
        Optional<FunctionTemplate> template = descriptor.functionTemplate(functionId);
        boolean openLockedEmpty = CatalogFunctionBinding.isOpenLockedEmptyTemplate(descriptor, template);
        if (openLockedEmpty) {
            CatalogFunctionBinding.rejectOpenLockedStructureMutation(request);
        }
        if (request.properties() != null) {
            List<PropertyItem> items = openLockedEmpty
                    ? List.of()
                    : (descriptor.fixedFunctions()
                            ? request.properties()
                            : List.of());
            if (descriptor.fixedFunctions() && template.isPresent()) {
                items = CatalogFunctionBinding.constrainFixedProperties(items, template.get());
            }
            entity.setOptionSchema(JsonMaps.write(PropertySchemas.toValueMap(items)));
            store.properties().replaceFunctionProperties(entity.getId(), items);
        }
        String previousAccess = entity.getAccessType();
        String effectiveAccess = request.accessType() != null && !request.accessType().isBlank()
                ? request.accessType()
                : previousAccess;
        boolean accessChanged = request.accessType() != null && !request.accessType().isBlank()
                && !request.accessType().equalsIgnoreCase(previousAccess);
        if (request.accessType() != null && !request.accessType().isBlank()) {
            if (descriptor.fixedFunctions() && template.isPresent()
                    && !template.get().accessType().equalsIgnoreCase(request.accessType())) {
                throw new IllegalArgumentException("FIXED 功能不允许修改 accessType");
            }
            if (openLockedEmpty && template.isPresent()
                    && !template.get().accessType().equalsIgnoreCase(request.accessType())) {
                throw new IllegalArgumentException("能力预置无参功能不允许修改 accessType");
            }
            if (accessChanged && !descriptor.fixedFunctions() && !openLockedEmpty) {
                boolean hasBinding = request.writeFields() != null || request.readFields() != null
                        || request.writeValueOptions() != null;
                if (!hasBinding) {
                    throw new IllegalArgumentException("修改 accessType 时必须同时提交 writeFields 或 readFields");
                }
            }
            entity.setAccessType(effectiveAccess);
        }
        if (request.accessPermission() != null) {
            entity.setAccessPermission(request.accessPermission());
        }
        if (request.sortIndex() != null) {
            entity.setSortIndex(request.sortIndex());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.writeAccessType() != null
                || request.writeValueOptions() != null
                || request.writeFields() != null
                || accessChanged) {
            ValueAccessType writeAccess;
            List<ValueOption> writeOpts;
            List<WriteFieldOption> fields;
            if (descriptor.fixedFunctions()) {
                writeAccess = ValueAccessType.STRUCT;
                fields = CatalogFunctionBinding.resolveFixedWriteFields(request.writeFields(), template);
                writeOpts = List.of();
            } else if (openLockedEmpty) {
                writeAccess = ValueAccessType.STRUCT;
                fields = List.of();
                writeOpts = List.of();
            } else if (descriptor.contractedParameters()) {
                writeAccess = CatalogFunctionBinding.resolveWriteAccess(request);
                FunctionTemplate contract = CatalogFunctionBinding.requireContractTemplate(descriptor, effectiveAccess);
                PayloadMode mode = writeAccess == ValueAccessType.VALUE ? PayloadMode.VALUE : PayloadMode.STRUCT;
                boolean isRead = "READ".equalsIgnoreCase(effectiveAccess);
                fields = isRead ? List.of()
                        : CatalogFunctionBinding.bindContractFields(contract, request.writeFields(), mode);
                writeOpts = CatalogFunctionBinding.constrainContractValueOptions(
                        request.writeValueOptions() == null ? List.of() : request.writeValueOptions(),
                        contract,
                        mode);
                if (isRead) {
                    store.properties().replaceReadFields(entity.getId(),
                            CatalogFunctionBinding.bindContractFields(contract, request.readFields(), mode));
                } else {
                    store.properties().replaceReadFields(entity.getId(), List.of());
                }
            } else {
                writeAccess = CatalogFunctionBinding.resolveWriteAccess(request);
                boolean isRead = "READ".equalsIgnoreCase(effectiveAccess);
                fields = isRead ? List.of() : CatalogFunctionBinding.nullSafeFields(request.writeFields());
                writeOpts = request.writeValueOptions() == null ? List.of() : request.writeValueOptions();
                if (isRead && request.readFields() != null) {
                    store.properties().replaceReadFields(entity.getId(),
                            CatalogFunctionBinding.nullSafeFields(request.readFields()));
                }
            }
            entity.setWriteAccessType(writeAccess.name());
            store.properties().replaceWriteOptions(entity.getId(), writeAccess, writeOpts, fields);
        }
        if (request.readFields() != null && !descriptor.fixedFunctions() && !openLockedEmpty
                && !descriptor.contractedParameters()) {
            store.properties().replaceReadFields(entity.getId(),
                    CatalogFunctionBinding.nullSafeFields(request.readFields()));
        }
        if (request.publishTopicSlot() != null) {
            entity.setPublishTopicSlot(request.publishTopicSlot());
        }
        if (request.subscribeTopicSlot() != null) {
            entity.setSubscribeTopicSlot(request.subscribeTopicSlot());
        }
        if (request.payloadEncoding() != null && !request.payloadEncoding().isBlank()) {
            entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
        }
        if (request.writeFields() != null || request.readFields() != null || request.writeValueOptions() != null
                || PayloadDefinitionResolver.hasDirectPayload(request)) {
            if (!PayloadDefinitionResolver.hasDirectPayload(request)) {
                PayloadDefinitionResolver.syncFromLegacyFields(
                        entity,
                        store.properties().listWriteFields(entity.getId()),
                        store.properties().listReadFields(entity.getId()),
                        store.properties().listWriteValueOptions(entity.getId()));
            } else {
                PayloadDefinitionResolver.applyPayloadFromRequest(entity, request);
            }
            store.updateFunction(entity);
        }
        if (request.readValueOptions() != null) {
            List<ValueOption> readOpts = request.readValueOptions();
            if (openLockedEmpty) {
                readOpts = List.of();
            } else if (descriptor.fixedFunctions() && template.isPresent()) {
                readOpts = readOpts.isEmpty()
                        ? List.of()
                        : CatalogFunctionBinding.constrainFixedValueOptions(readOpts, template.get());
            }
            store.properties().replaceReadValueOptions(entity.getId(), readOpts);
        }
        return store.updateFunction(entity);
    }

    // ——— Device ———

    /**
     * 创建设备
     * 这个方式是创建指定设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @param request 设备写请求
     * @return 设备实体
     * @throws IllegalArgumentException 如果设备编码为空或设备名称为空或设备编码已存在
     */
    @Transactional
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

    /**
     * 更新设备
     * 这个方式是更新指定设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @param deviceCode 设备编码
     * @param request    设备更新请求
     * @return 设备实体
     * @throws IllegalArgumentException 如果设备不存在或设备编码为空或设备名称为空或设备编码已存在
     */
    @Transactional
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

    /**
     * 更新设备端点
     * 这个方式是更新指定设备端点，设备端点包含了设备端点ID、设备端点编码、设备端点名称、设备端点描述等。
     * 
     * @param endpointId 设备端点ID
     * @param request    设备端点写请求
     * @return 设备端点实体
     * @throws IllegalArgumentException 如果设备端点不存在或设备端点编码为空或设备端点名称为空或设备端点编码已存在
     */
    @Transactional
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
                CatalogConnectionSupport.validateAddress(descriptor, items);
                entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
                store.properties().replaceEndpointProperties(entity.getId(), items);
            }
        }
        return store.updateEndpoint(entity);
    }

    /**
     * 注册设备
     * 这个方式是注册指定设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @param request 设备注册请求
     * @return 设备实体
     * @throws IllegalArgumentException 如果设备编码为空或设备名称为空或设备编码已存在
     */
    @Transactional
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

    /**
     * 创建设备端点
     * 这个方式是创建设备端点，设备端点包含了设备端点ID、设备端点编码、设备端点名称、设备端点描述等。
     * 
     * @param deviceCodeOrId 设备编码或ID
     * @param request        设备端点写请求
     * @return 设备端点实体
     * @throws IllegalArgumentException 如果设备端点编码为空或设备端点名称为空或设备端点编码已存在
     */
    @Transactional
    public DeviceEndpointEntity createEndpoint(String deviceCodeOrId, DeviceEndpointWriteRequest request) {
        if (request == null || request.channelId() == null || request.channelId().isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        DeviceEntity device = requireDevice(deviceCodeOrId);
        ChannelEntity channel = store.findChannel(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + request.channelId()));
        CapabilityDescriptor descriptor = requireCapability(channel.getCapabilityType());
        List<PropertyItem> items = resolveProperties(request.properties(), request.address());
        CatalogConnectionSupport.validateAddress(descriptor, items);
        DeviceEndpointEntity entity = new DeviceEndpointEntity();
        entity.setDeviceId(device.getId());
        entity.setChannelId(channel.getId());
        entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
        DeviceEndpointEntity saved = store.saveEndpoint(entity);
        store.properties().replaceEndpointProperties(saved.getId(), items);
        return saved;
    }

    /**
     * 列出设备
     * 这个方式是列出所有设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @return 设备实体列表
     */
    public List<DeviceEntity> listDevices() {
        return store.listDevices();
    }

    /**
     * 分页设备
     * 这个方式是分页设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @param page 页码
     * @param size 每页大小
     * @return 设备实体列表
     */
    public PageResult<DeviceEntity> pageDevices(int page, int size) {
        return store.pageDevices(page, size);
    }

    /**
     * 获取设备
     * 这个方式是获取指定设备，设备包含了设备ID、设备编码、设备名称、设备描述等。
     * 
     * @param deviceCodeOrId 设备编码或ID
     * @return 设备实体
     * @throws IllegalArgumentException 如果设备不存在或设备编码为空或设备名称为空或设备编码已存在
     */
    public DeviceEntity requireDevice(String deviceCodeOrId) {
        return store.resolveDevice(deviceCodeOrId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCodeOrId));
    }

    /**
     * 构建功能命令
     * 这个方式是构建指定功能命令，功能命令包含了功能命令ID、功能命令名称、功能命令参数、功能命令写选项、功能命令读选项、功能命令值访问类型、功能命令值访问权限、功能命令值访问选项等。
     * 
     * @param deviceCode 设备编码
     * @param request    设备命令请求
     * @return 功能命令
     */
    public FunctionCommand buildCommand(String deviceCode, DeviceCommandRequest request) {
        if (request == null || request.functionId() == null || request.functionId().isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        DeviceEntity device = requireDevice(deviceCode);
        requireEnabled(device.getEnabled(), "设备已停用: " + device.getDeviceCode());
        ProductFunctionEntity function = store.findFunction(device.getProductId(), request.functionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "功能未配置到产品: " + device.getProductId() + "/" + request.functionId()));
        requireFunctionPermission(function);
        DeviceEndpointBinding endpoint = requireEndpointForFunction(device, function);

        Map<String, Object> caller = request.arguments() == null ? Map.of() : request.arguments();

        PayloadDefinitionResolver.Definition definition = PayloadDefinitionResolver.resolve(function,
                store.properties());
        Map<String, Object> legacyOverrides = PropertySchemas.toValueMap(
                store.loadDeviceOverrides(device, function.getFunctionId()));
        Map<String, Object> pathOverrides = store.properties().listDeviceFieldOverrides(
                device.getId(), function.getFunctionId());
        Map<String, Object> deviceFieldOverrides = new LinkedHashMap<>(legacyOverrides);
        deviceFieldOverrides.putAll(pathOverrides);
        validateFieldOverrides(function, deviceFieldOverrides);

        Map<String, Object> payload = PayloadDefinitionResolver.assemble(definition, caller, deviceFieldOverrides);

        Map<String, Object> deliveryHints = new LinkedHashMap<>();
        if (function.getAccessType() != null && !function.getAccessType().isBlank()) {
            deliveryHints.put("accessType", function.getAccessType());
        }
        if ("MQTT".equalsIgnoreCase(function.getCapabilityType())) {
            TopicCatalog catalog = TopicCatalog.fromAddressMap(endpoint.address().values());
            Map<String, String> topicOverrides = store.properties().listDeviceTopicOverrides(
                    device.getId(), function.getFunctionId());
            validateTopicOverrides(function, endpoint, topicOverrides);
            var resolved = TopicRouteResolver.resolve(
                    catalog,
                    definition.route(),
                    topicOverrides,
                    !"READ".equalsIgnoreCase(function.getAccessType()));
            if (resolved.publishTopic() != null) {
                deliveryHints.put(TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT, resolved.publishTopic());
            }
            if ("READ".equalsIgnoreCase(function.getAccessType())) {
                deliveryHints.put("mqtt.subscribeOnly", "true");
                if (resolved.subscribeTopic() != null) {
                    deliveryHints.put(TopicRouteResolver.MQTT_SUBSCRIBE_TOPIC_HINT, resolved.subscribeTopic());
                }
            }
        }

        return FunctionCommand.of(
                device.getDeviceCode(),
                function.getFunctionId(),
                payload,
                deliveryHints);
    }

    /**
     * 列出产品功能
     * 这个方式是列出指定产品功能，产品功能包含了产品功能ID、产品功能类型、产品功能参数、产品功能写选项、产品功能读选项、产品功能值访问类型、产品功能值访问权限、产品功能值访问选项等。
     * 
     * @param productId 产品ID
     * @return 产品功能实体列表
     */
    public List<FunctionFormView> productFunctions(String productId) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        return projectFunctionForms(store.listFunctions(productId), Map.of());
    }

    public List<ProductFunctionView> listProductFunctionViews(String productId) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        List<ProductFunctionEntity> functions = store.listFunctions(productId);
        Map<String, List<PropertyItem>> props = store.loadFunctionProperties(functions);
        Map<String, FunctionOptionBundle> options = store.properties().loadFunctionOptions(
                functions.stream().map(f -> f.getId()).toList());
        return functions.stream()
                .map(function -> views.toProductFunctionView(
                        function,
                        props.getOrDefault(function.getId(), List.of()),
                        options.getOrDefault(function.getId(), FunctionOptionBundle.empty())))
                .toList();
    }

    public FunctionFormView productFunction(String productId, String functionId) {
        ProductFunctionEntity function = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        return views.toForm(function, Map.of());
    }

    public List<FunctionFormView> deviceFunctions(String deviceCode) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        return projectFunctionForms(store.listFunctions(device.getProductId()), store.loadAllDeviceOverrides(device));
    }

    public FunctionFormView deviceFunction(String deviceCode, String functionId) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return views.toForm(function, store.loadAllDeviceOverrides(device));
    }

    public ProductFunctionView toProductFunctionView(ProductFunctionEntity function) {
        return views.toProductFunctionView(function);
    }

    public ChannelView toChannelView(ChannelEntity entity) {
        return views.toChannelView(entity);
    }

    public DeviceView toDeviceView(DeviceEntity entity) {
        return views.toDeviceView(entity);
    }

    public DeviceEndpointView toEndpointView(DeviceEndpointEntity entity) {
        return views.toEndpointView(entity);
    }

    public Map<String, Object> deviceFieldOverrides(String deviceCode, String functionId) {
        DeviceEntity device = requireDevice(deviceCode);
        store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return store.properties().listDeviceFieldOverrides(device.getId(), functionId);
    }

    @Transactional
    public void replaceDeviceFieldOverrides(String deviceCode, String functionId, Map<String, Object> overrides) {
        DeviceEntity device = requireDevice(deviceCode);
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        Map<String, Object> safe = overrides == null ? Map.of() : overrides;
        validateFieldOverrides(function, safe);
        store.properties().replaceDeviceFieldOverrides(device.getId(), functionId, safe);
    }

    public Map<String, String> deviceTopicOverrides(String deviceCode, String functionId) {
        DeviceEntity device = requireDevice(deviceCode);
        store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return store.properties().listDeviceTopicOverrides(device.getId(), functionId);
    }

    @Transactional
    public void replaceDeviceTopicOverrides(String deviceCode, String functionId, Map<String, String> overrides) {
        DeviceEntity device = requireDevice(deviceCode);
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        Map<String, String> safe = overrides == null ? Map.of() : overrides;
        DeviceEndpointBinding endpoint = null;
        if ("MQTT".equalsIgnoreCase(function.getCapabilityType())) {
            endpoint = requireEndpointForFunction(device, function);
        }
        validateTopicOverrides(function, endpoint, safe);
        store.properties().replaceDeviceTopicOverrides(device.getId(), functionId, safe);
    }

    public List<DeviceEndpointView> deviceEndpoints(String deviceCode) {
        DeviceEntity device = requireDevice(deviceCode);
        List<DeviceEndpointEntity> rows = store.listEndpointEntities(device.getId());
        Map<String, List<PropertyItem>> props = store.loadEndpointProperties(rows);
        return rows.stream()
                .map(row -> views.toEndpointView(row, props.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    public PageResult<ChannelView> pageChannelViews(int page, int size) {
        PageResult<ChannelEntity> raw = store.pageChannels(page, size);
        Map<String, List<PropertyItem>> props = store.loadChannelProperties(raw.items());
        return new PageResult<>(
                raw.items().stream()
                        .map(item -> views.toChannelView(item, props.getOrDefault(item.getId(), List.of())))
                        .toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    public PageResult<DeviceView> pageDeviceViews(int page, int size) {
        PageResult<DeviceEntity> raw = store.pageDevices(page, size);
        Map<String, Map<String, List<PropertyItem>>> overrides = store.loadAllDeviceOverrides(raw.items());
        return new PageResult<>(
                raw.items().stream()
                        .map(item -> views.toDeviceView(
                                item,
                                overrides.getOrDefault(item.getId(), Map.of()),
                                views.isLoaded(item.getDeviceCode())))
                        .toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    private List<FunctionFormView> projectFunctionForms(
            List<ProductFunctionEntity> functions, Map<String, List<PropertyItem>> overrides) {
        Map<String, List<PropertyItem>> props = store.loadFunctionProperties(functions);
        Map<String, FunctionOptionBundle> options = store.properties().loadFunctionOptions(
                functions.stream().map(f -> f.getId()).toList());
        return functions.stream()
                .map(function -> views.toForm(
                        function,
                        overrides,
                        props.getOrDefault(function.getId(), List.of()),
                        options.getOrDefault(function.getId(), FunctionOptionBundle.empty())))
                .toList();
    }

    private static List<PropertyItem> resolveProperties(List<PropertyItem> properties, Map<String, Object> legacy) {
        if (properties != null) {
            return properties;
        }
        return PropertySchemas.fromValueMap(legacy == null ? Map.of() : legacy);
    }

    /**
     * 解析功能覆盖
     * 这个方式是解析指定功能覆盖，功能覆盖包含了功能覆盖ID、功能覆盖名称、功能覆盖类型、功能覆盖描述等。
     * 
     * @param overrides 覆盖列表
     * @param legacy    遗产覆盖
     * @return 覆盖列表
     */
    private static Map<String, List<PropertyItem>> resolveFunctionOverrides(
            Map<String, List<PropertyItem>> overrides, Map<String, Object> legacy) {
        if (overrides != null) {
            return overrides;
        }
        return CatalogStore.parseLegacyOverrides(legacy);
    }

    /**
     * 转换覆盖为遗产覆盖
     * 这个方式是转换指定覆盖为遗产覆盖，覆盖包含了覆盖ID、覆盖名称、覆盖类型、覆盖描述等。
     * 
     * @param overrides 覆盖列表
     * @return 遗产覆盖
     */
    private static Map<String, Object> toLegacyOverrideMap(Map<String, List<PropertyItem>> overrides) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (overrides == null) {
            return result;
        }
        overrides.forEach((functionId, items) -> result.put(functionId, PropertySchemas.toValueMap(items)));
        return result;
    }

    private static void requireEnabled(Boolean enabled, String message) {
        if (Boolean.FALSE.equals(enabled)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireFunctionPermission(ProductFunctionEntity function) {
        int permission = function.getAccessPermission() == null
                ? AccessPermission.WRITE.code()
                : function.getAccessPermission();
        if ("WRITE".equalsIgnoreCase(function.getAccessType())
                && !AccessPermission.contains(permission, AccessPermission.WRITE)) {
            throw new IllegalArgumentException("功能未授予写权限: " + function.getFunctionId());
        }
        if ("READ".equalsIgnoreCase(function.getAccessType())
                && !AccessPermission.contains(permission, AccessPermission.READ)
                && permission != AccessPermission.WRITE.code()) {
            throw new IllegalArgumentException("功能未授予读权限: " + function.getFunctionId());
        }
    }

    private DeviceEndpointBinding requireEndpointForFunction(DeviceEntity device, ProductFunctionEntity function) {
        String capability = function.getCapabilityType();
        List<DeviceEndpointBinding> matched = store.findEndpoints(device.getDeviceCode()).stream()
                .filter(endpoint -> capability != null && capability.equalsIgnoreCase(endpoint.capabilityType()))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalArgumentException(
                    "设备未绑定 " + capability + " 通道: " + device.getDeviceCode() + "/" + function.getFunctionId());
        }
        DeviceEndpointBinding chosen = matched.getFirst();
        ChannelEntity channel = store.findChannel(chosen.channelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + chosen.channelId()));
        requireEnabled(channel.getEnabled(), "通道已停用: " + channel.getCode());
        return chosen;
    }

    private void validateFieldOverrides(ProductFunctionEntity function, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Set<String> allowed = allowedOverrideFields(function);
        List<String> unknown = overrides.keySet().stream()
                .filter(key -> key != null && !key.isBlank() && !allowed.contains(key))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "设备字段覆盖不在功能契约内: " + unknown + "（功能 " + function.getFunctionId() + "）");
        }
    }

    private Set<String> allowedOverrideFields(ProductFunctionEntity function) {
        Set<String> allowed = new LinkedHashSet<>();
        boolean read = "READ".equalsIgnoreCase(function.getAccessType());
        List<WriteFieldOption> fields = read
                ? store.properties().listReadFields(function.getId())
                : store.properties().listWriteFields(function.getId());
        for (WriteFieldOption field : fields == null ? List.<WriteFieldOption>of() : fields) {
            if (field.field() != null && !field.field().isBlank()) {
                allowed.add(field.field());
            }
            if (field.callerField() != null && !field.callerField().isBlank()) {
                allowed.add(field.callerField());
            }
        }
        for (PropertyItem item : store.loadFunctionProperties(function)) {
            if (item.attribute() != null && !item.attribute().isBlank()) {
                allowed.add(item.attribute());
            }
        }
        return allowed;
    }

    private void validateTopicOverrides(
            ProductFunctionEntity function, DeviceEndpointBinding endpoint, Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(TopicCatalog.DEFAULT_PUB);
        allowed.add(TopicCatalog.DEFAULT_SUB);
        if (function.getPublishTopicSlot() != null && !function.getPublishTopicSlot().isBlank()) {
            allowed.add(function.getPublishTopicSlot());
        }
        if (function.getSubscribeTopicSlot() != null && !function.getSubscribeTopicSlot().isBlank()) {
            allowed.add(function.getSubscribeTopicSlot());
        }
        if (endpoint != null) {
            try {
                allowed.addAll(TopicCatalog.fromAddressMap(endpoint.address().values()).slots().keySet());
            } catch (IllegalArgumentException ignored) {
                // 地址尚未配齐时只允许默认 slot
            }
        }
        List<String> unknown = overrides.keySet().stream()
                .filter(key -> key != null && !key.isBlank() && !allowed.contains(key))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "设备主题覆盖不在已知 slot 内: " + unknown + "（功能 " + function.getFunctionId() + "）");
        }
    }
}
