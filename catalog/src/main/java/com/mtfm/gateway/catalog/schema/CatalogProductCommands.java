package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.payload.PayloadDefinitionResolver;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.payload.ScaleTransform;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 产品与产品功能的创建、更新、导入。
 */
@Component
final class CatalogProductCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogProductCommands(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    ProductEntity createProduct(ProductWriteRequest request) {
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
        store.findProduct(productId).orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
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
        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setProductId(productId);
        entity.setFunctionId(request.functionId());
        entity.setCapabilityType(request.capabilityType());
        return persistFunction(entity, request, descriptor, template, true);
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
        return persistFunction(entity, request, descriptor, template, false);
    }

    /**
     * 创建全量落库；更新先把未提交字段补成库值再走同一套绑定。
     */
    private ProductFunctionEntity persistFunction(
            ProductFunctionEntity entity,
            ProductFunctionWriteRequest request,
            CapabilityDescriptor descriptor,
            Optional<FunctionTemplate> template,
            boolean creating) {
        boolean openLockedEmpty = CatalogOpenFunctionBinding.isOpenLockedEmptyTemplate(descriptor, template);
        if (openLockedEmpty) {
            CatalogOpenFunctionBinding.rejectOpenLockedStructureMutation(request);
        }
        String previousAccess = entity.getAccessType();
        boolean accessChanged = !creating
                && request.accessType() != null
                && !request.accessType().isBlank()
                && !request.accessType().equalsIgnoreCase(previousAccess);
        if (accessChanged) {
            rejectIllegalAccessTypeChange(descriptor, template, openLockedEmpty, request);
        }
        String accessType = resolveAccessType(creating, openLockedEmpty, request, template, previousAccess);
        entity.setAccessType(accessType);

        List<PropertyItem> propertyItems = null;
        if (creating || request.properties() != null) {
            propertyItems = resolvePersistedProperties(descriptor, template, request, openLockedEmpty);
        }

        boolean rebind = creating
                || accessChanged
                || request.writeAccessType() != null
                || request.writeValueOptions() != null
                || request.writeFields() != null
                || request.readFields() != null
                || request.readValueOptions() != null;
        CatalogFunctionBinding.FunctionOptionPlan plan = null;
        if (rebind) {
            ProductFunctionWriteRequest bindRequest = creating
                    ? request
                    : overlayExistingBindings(request, entity);
            plan = CatalogFunctionBinding.bindRequest(
                    descriptor, template, bindRequest, accessType, openLockedEmpty);
            entity.setWriteAccessType(plan.writeAccess().name());
        }

        if (creating) {
            entity.setAccessPermission(request.accessPermission() != null
                    ? request.accessPermission()
                    : template.map(ft -> ft.accessPermission())
                            .orElseGet(() -> "READ".equalsIgnoreCase(accessType)
                                    ? AccessPermission.READ.code()
                                    : AccessPermission.WRITE.code()));
            entity.setSortIndex(request.sortIndex());
            entity.setDescription(request.description() != null
                    ? request.description()
                    : template.map(ft -> ft.description()).orElse(null));
            entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
        } else {
            if (request.accessPermission() != null) {
                entity.setAccessPermission(request.accessPermission());
            }
            if (request.sortIndex() != null) {
                entity.setSortIndex(request.sortIndex());
            }
            if (request.description() != null) {
                entity.setDescription(request.description());
            }
            if (request.payloadEncoding() != null && !request.payloadEncoding().isBlank()) {
                entity.setPayloadEncoding(PayloadEncoding.from(request.payloadEncoding()).wire());
            }
        }
        if (request.publishTopicSlot() != null) {
            entity.setPublishTopicSlot(request.publishTopicSlot());
        }
        if (request.subscribeTopicSlot() != null) {
            entity.setSubscribeTopicSlot(request.subscribeTopicSlot());
        }
        applyReplyAndSchedule(entity, request);
        if (PayloadDefinitionResolver.hasDirectPayload(request)) {
            PayloadDefinitionResolver.applyPayloadFromRequest(entity, request);
        } else if (plan != null) {
            PayloadDefinitionResolver.syncFromLegacyFields(
                    entity, plan.writeFields(), plan.readFields(), plan.writeValueOptions());
        }

        ProductFunctionEntity saved = creating ? store.saveFunction(entity) : store.updateFunction(entity);
        if (propertyItems != null) {
            store.properties().replaceFunctionProperties(saved.getId(), propertyItems);
        }
        if (plan != null) {
            store.properties().replaceWriteOptions(
                    saved.getId(), plan.writeAccess(), plan.writeValueOptions(), plan.writeFields());
            store.properties().replaceReadFields(saved.getId(), plan.readFields());
            store.properties().replaceReadValueOptions(saved.getId(), plan.readValueOptions());
        }
        return saved;
    }

    private static void rejectIllegalAccessTypeChange(
            CapabilityDescriptor descriptor,
            Optional<FunctionTemplate> template,
            boolean openLockedEmpty,
            ProductFunctionWriteRequest request) {
        if (descriptor.fixedFunctions() && template.isPresent()
                && !template.get().accessType().equalsIgnoreCase(request.accessType())) {
            throw new IllegalArgumentException("FIXED 功能不允许修改 accessType");
        }
        if (openLockedEmpty && template.isPresent()
                && !template.get().accessType().equalsIgnoreCase(request.accessType())) {
            throw new IllegalArgumentException("能力预置无参功能不允许修改 accessType");
        }
        if (!descriptor.fixedFunctions() && !openLockedEmpty) {
            boolean hasBinding = request.writeFields() != null || request.readFields() != null
                    || request.writeValueOptions() != null;
            if (!hasBinding) {
                throw new IllegalArgumentException("修改 accessType 时必须同时提交 writeFields 或 readFields");
            }
        }
    }

    private static String resolveAccessType(
            boolean creating,
            boolean openLockedEmpty,
            ProductFunctionWriteRequest request,
            Optional<FunctionTemplate> template,
            String previousAccess) {
        if (openLockedEmpty) {
            return template.map(ft -> ft.accessType()).orElse("WRITE");
        }
        if (request.accessType() != null && !request.accessType().isBlank()) {
            return request.accessType();
        }
        if (!creating && previousAccess != null && !previousAccess.isBlank()) {
            return previousAccess;
        }
        return template.map(ft -> ft.accessType()).orElse("WRITE");
    }

    private static List<PropertyItem> resolvePersistedProperties(
            CapabilityDescriptor descriptor,
            Optional<FunctionTemplate> template,
            ProductFunctionWriteRequest request,
            boolean openLockedEmpty) {
        if (openLockedEmpty || !descriptor.fixedFunctions()) {
            return List.of();
        }
        List<PropertyItem> items = CatalogOpenFunctionBinding.resolveFunctionProperties(request, template);
        if (template.isPresent()) {
            items = CatalogFixedFunctionBinding.constrainFixedProperties(items, template.get());
        }
        return items;
    }

    private ProductFunctionWriteRequest overlayExistingBindings(
            ProductFunctionWriteRequest request, ProductFunctionEntity entity) {
        return new ProductFunctionWriteRequest(
                request.functionId(),
                request.accessType(),
                request.accessPermission(),
                request.capabilityType(),
                request.writeAccessType() != null ? request.writeAccessType() : entity.getWriteAccessType(),
                request.properties(),
                request.writeValueOptions() != null
                        ? request.writeValueOptions()
                        : store.properties().listWriteValueOptions(entity.getId()),
                request.writeFields() != null
                        ? request.writeFields()
                        : store.properties().listWriteFields(entity.getId()),
                request.readFields() != null
                        ? request.readFields()
                        : store.properties().listReadFields(entity.getId()),
                request.readValueOptions() != null
                        ? request.readValueOptions()
                        : store.properties().listReadValueOptions(entity.getId()),
                request.sortIndex(),
                request.description(),
                request.publishTopicSlot(),
                request.subscribeTopicSlot(),
                request.payloadMode() != null ? request.payloadMode() : entity.getPayloadMode(),
                request.payloadEncoding(),
                request.replyTopicSlot(),
                request.correlationPath(),
                request.correlationCommandPath(),
                request.replyTimeoutMs(),
                request.scheduleIntervalMs(),
                request.scheduleEnabled(),
                request.scaleOp(),
                request.scaleOperand());
    }

    private ProductFunctionEntity createFunctionFromTemplate(
            String productId, String capabilityType, FunctionTemplate template) {
        CapabilityDescriptor descriptor = support.requireCapability(capabilityType);
        List<PropertyItem> properties = PropertySchemas.toPropertyItems(template.parameters());
        List<WriteFieldOption> writeFields = descriptor.contractedParameters()
                ? CatalogContractFunctionBinding.bindContractFields(template, null, PayloadMode.STRUCT)
                : CatalogFixedFunctionBinding.templateWriteFields(template);
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
                null,
                null,
                null,
                null,
                null,
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
        if (CatalogOpenFunctionBinding.isOpenLockedEmptyTemplate(descriptor, locked)) {
            return entity;
        }
        PayloadMode mode = PayloadMode.from(entity.getPayloadMode());
        boolean read = "READ".equalsIgnoreCase(entity.getAccessType());
        if (descriptor.contractedParameters()) {
            FunctionTemplate contract = CatalogContractFunctionBinding.requireContractTemplate(descriptor,
                    entity.getAccessType());
            List<WriteFieldOption> existing = read
                    ? store.properties().listReadFields(entity.getId())
                    : store.properties().listWriteFields(entity.getId());
            List<WriteFieldOption> known = CatalogFunctionSchemas.filterKnownFields(existing, contract);
            List<WriteFieldOption> bound = CatalogContractFunctionBinding.bindContractFields(contract,
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
            List<WriteFieldOption> known = CatalogFunctionSchemas.filterKnownFields(existing, template);
            List<WriteFieldOption> bound = CatalogFixedFunctionBinding.constrainFixedWriteFields(
                    known.isEmpty() ? CatalogFixedFunctionBinding.templateWriteFields(template) : known, template);
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
        if (request.replyTimeoutMs() != null) {
            entity.setReplyTimeoutMs(request.replyTimeoutMs() <= 0 ? null : request.replyTimeoutMs());
        }
        if (request.scheduleIntervalMs() != null) {
            entity.setScheduleIntervalMs(request.scheduleIntervalMs() <= 0 ? null : request.scheduleIntervalMs());
        }
        if (request.scheduleEnabled() != null) {
            entity.setScheduleEnabled(request.scheduleEnabled());
        }
        if (request.scaleOp() != null || request.scaleOperand() != null) {
            applyScale(entity, request.scaleOp(), request.scaleOperand());
        }
    }

    /**
     * 取消换算必须写成 {@code none} / 空串，不能落 null：MyBatis-Plus updateById 默认跳过 null。
     */
    private static void applyScale(ProductFunctionEntity entity, String scaleOp, String scaleOperand) {
        String op = persistScaleOp(scaleOp);
        entity.setScaleOp(op);
        if (ScaleTransform.NONE.equals(op)) {
            entity.setScaleOperand("");
            return;
        }
        if (scaleOperand != null) {
            entity.setScaleOperand(scaleOperand.trim());
        }
    }

    private static String persistScaleOp(String raw) {
        if (raw == null || raw.isBlank() || ScaleTransform.NONE.equalsIgnoreCase(raw.trim())) {
            return ScaleTransform.NONE;
        }
        return raw.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
