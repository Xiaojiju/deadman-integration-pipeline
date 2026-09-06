package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.payload.PayloadDefinitionResolver;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 产品与产品功能的创建、更新、导入。
 */
final class CatalogProductCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogProductCommands(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    ProductEntity createProduct(ProductWriteRequest request) {
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

    ProductEntity updateProduct(String productId, ProductWriteRequest request) {
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

    List<ProductFunctionEntity> importCapabilityFunctions(String productId, String capabilityType) {
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        CapabilityDescriptor descriptor = support.requireCapability(capabilityType);
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

    ProductFunctionEntity createFunction(String productId, ProductFunctionWriteRequest request) {
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
        CapabilityDescriptor descriptor = support.requireCapability(request.capabilityType());
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
        String accessType = openLockedEmpty
                ? template.map(t -> t.accessType()).orElse("WRITE")
                : request.accessType() != null
                        ? request.accessType()
                        : template.map(t -> t.accessType()).orElse("WRITE");
        CatalogFunctionBinding.FunctionOptionPlan plan = CatalogFunctionBinding.bindRequest(
                descriptor, template, request, accessType, openLockedEmpty);
        ValueAccessType writeAccess = plan.writeAccess();
        List<WriteFieldOption> writeFields = plan.writeFields();
        List<WriteFieldOption> readFields = plan.readFields();
        List<ValueOption> writeValueOptions = plan.writeValueOptions();
        List<ValueOption> readValueOptions = plan.readValueOptions();

        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setProductId(productId);
        entity.setFunctionId(request.functionId());
        entity.setAccessType(accessType);
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
        applyReplyAndSchedule(entity, request);
        entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
        ProductFunctionEntity saved = store.saveFunction(entity);
        store.properties().replaceFunctionProperties(saved.getId(), items);
        store.properties().replaceWriteOptions(saved.getId(), writeAccess, writeValueOptions, writeFields);
        store.properties().replaceReadFields(saved.getId(), readFields);
        store.properties().replaceReadValueOptions(saved.getId(), readValueOptions);
        return saved;
    }

    ProductFunctionEntity updateFunction(String productId, String functionId,
            ProductFunctionWriteRequest request) {
        ProductFunctionEntity entity = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        if (request == null) {
            return entity;
        }
        CapabilityDescriptor descriptor = support.requireCapability(entity.getCapabilityType());
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
            CatalogFunctionBinding.FunctionOptionPlan plan = CatalogFunctionBinding.bindRequest(
                    descriptor, template, request, effectiveAccess, openLockedEmpty);
            entity.setWriteAccessType(plan.writeAccess().name());
            store.properties().replaceWriteOptions(
                    entity.getId(), plan.writeAccess(), plan.writeValueOptions(), plan.writeFields());
            if (descriptor.fixedFunctions() || openLockedEmpty || descriptor.contractedParameters() || accessChanged) {
                store.properties().replaceReadFields(entity.getId(), plan.readFields());
            }
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
        applyReplyAndSchedule(entity, request);
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

    private ProductFunctionEntity createFunctionFromTemplate(
            String productId, String capabilityType, FunctionTemplate template) {
        CapabilityDescriptor descriptor = support.requireCapability(capabilityType);
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

    private ProductFunctionEntity refreshFunctionFromTemplate(
            String capabilityType, FunctionTemplate template, ProductFunctionEntity entity) {
        if (entity.getCapabilityType() != null
                && !entity.getCapabilityType().equalsIgnoreCase(capabilityType)) {
            return entity;
        }
        CapabilityDescriptor descriptor = support.requireCapability(capabilityType);
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

    private static void applyReplyAndSchedule(ProductFunctionEntity entity, ProductFunctionWriteRequest request) {
        if (request.replyTopicSlot() != null) {
            entity.setReplyTopicSlot(blankToNull(request.replyTopicSlot()));
        }
        if (request.correlationPath() != null) {
            entity.setCorrelationPath(blankToNull(request.correlationPath()));
        }
        if (request.correlationCommandPath() != null) {
            entity.setCorrelationCommandPath(blankToNull(request.correlationCommandPath()));
        }
        if (request.resultPath() != null) {
            entity.setResultPath(blankToNull(request.resultPath()));
        }
        if (request.replyTimeoutMs() != null) {
            entity.setReplyTimeoutMs(request.replyTimeoutMs() <= 0 ? null : request.replyTimeoutMs());
        }
        if (request.scheduleIntervalMs() != null) {
            entity.setScheduleIntervalMs(request.scheduleIntervalMs() <= 0 ? null : request.scheduleIntervalMs());
        }
        if (request.scheduleEnabled() != null) {
            entity.setScheduleEnabled(request.scheduleEnabled());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
