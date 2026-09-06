package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointView;
import com.mtfm.gateway.catalog.dto.DeviceView;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.ProductFunctionView;
import com.mtfm.gateway.catalog.dto.ProductView;
import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.catalog.store.FunctionOptionBundle;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaForms;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 目录对外视图投影：表单 / 功能 / 通道 / 设备。列表路径可传入已批量加载的属性与 Option。
 */
final class CatalogFormViews {

    private final CatalogStore store;
    private final CapabilityRegistrar registrar;
    private final ObjectProvider<DriverRegistry> driverRegistry;

    CatalogFormViews(
            CatalogStore store,
            CapabilityRegistrar registrar,
            ObjectProvider<DriverRegistry> driverRegistry) {
        this.store = store;
        this.registrar = registrar;
        this.driverRegistry = driverRegistry;
    }

    SupportedFunctionView toSupportedFunction(FunctionTemplate template) {
        List<SchemaField> schema = template.parameters();
        List<PropertyItem> properties = PropertySchemas.toPropertyItems(schema);
        Map<String, List<ValueOption>> choices = PropertySchemas.choiceOptionsByField(schema);
        return new SupportedFunctionView(
                template.functionId(),
                template.description(),
                template.accessType(),
                template.accessPermission(),
                ValueAccessType.VALUE.name(),
                SchemaForms.bind(schema, Map.of()),
                properties,
                CatalogFunctionBinding.flattenChoiceOptions(choices),
                choices);
    }

    FunctionFormView toForm(ProductFunctionEntity function, Map<String, List<PropertyItem>> deviceOverrides) {
        List<PropertyItem> base = store.loadFunctionProperties(function);
        FunctionOptionBundle bundle = store.properties().loadFunctionOptions(function.getId());
        return toForm(function, deviceOverrides, base, bundle);
    }

    FunctionFormView toForm(
            ProductFunctionEntity function,
            Map<String, List<PropertyItem>> deviceOverrides,
            List<PropertyItem> base,
            FunctionOptionBundle bundle) {
        FunctionOptionBundle options = bundle == null ? FunctionOptionBundle.empty() : bundle;
        List<PropertyItem> properties = base == null ? List.of() : base;
        List<PropertyItem> overrideItems = deviceOverrides.getOrDefault(function.getFunctionId(), List.of());
        Map<String, Object> values = new LinkedHashMap<>(PropertySchemas.toValueMap(properties));
        values.putAll(PropertySchemas.toValueMap(overrideItems));
        ValueAccessType writeAccess = ValueAccessType.from(function.getWriteAccessType());
        boolean isRead = "READ".equalsIgnoreCase(function.getAccessType());
        List<WriteFieldOption> structFields = isRead ? options.readFields() : options.writeFields();
        Optional<FunctionTemplate> template = findTemplate(function.getCapabilityType(), function.getFunctionId());
        boolean contracted = registrar != null && function.getCapabilityType() != null
                && registrar.find(function.getCapabilityType()).map(descriptor -> descriptor.contractedParameters())
                        .orElse(false);
        List<SchemaField> schema;
        if (contracted && !structFields.isEmpty()) {
            schema = CatalogFunctionBinding.schemaFromWriteFields(structFields, true);
        } else if (template.isPresent()) {
            schema = template.get().parameters();
        } else if (!structFields.isEmpty()) {
            schema = CatalogFunctionBinding.schemaFromWriteFields(structFields, true);
        } else {
            schema = CatalogFunctionBinding.schemaFromProperties(properties);
        }
        List<FormField> fields = SchemaForms.bind(schema, values);
        List<ValueOption> writeOptions = options.writeValueOptions();
        boolean openStructForm = template.isEmpty() && !structFields.isEmpty();
        if (!contracted && writeOptions.isEmpty() && !openStructForm) {
            writeOptions = CatalogFunctionBinding.flattenWriteFieldOptions(structFields);
        }
        if (!contracted && writeOptions.isEmpty() && template.isPresent()) {
            writeOptions = CatalogFunctionBinding.flattenChoiceOptions(
                    PropertySchemas.choiceOptionsByField(template.get().parameters()));
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
                null,
                CatalogFunctionBinding.resolvePayloadMode(function, writeOptions));
    }

    ProductFunctionView toProductFunctionView(ProductFunctionEntity function) {
        return toProductFunctionView(
                function,
                store.loadFunctionProperties(function),
                store.properties().loadFunctionOptions(function.getId()));
    }

    ProductFunctionView toProductFunctionView(
            ProductFunctionEntity function,
            List<PropertyItem> props,
            FunctionOptionBundle bundle) {
        FunctionOptionBundle options = bundle == null ? FunctionOptionBundle.empty() : bundle;
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
                props == null ? List.of() : props,
                options.writeValueOptions(),
                options.writeFields(),
                options.readFields(),
                options.readValueOptions(),
                function.getSortIndex(),
                function.getPublishTopicSlot(),
                function.getSubscribeTopicSlot(),
                function.getPayloadMode(),
                function.getPayloadEncoding(),
                function.getReplyTopicSlot(),
                function.getCorrelationPath(),
                function.getCorrelationCommandPath(),
                function.getResultPath(),
                function.getReplyTimeoutMs(),
                function.getScheduleIntervalMs(),
                function.getScheduleEnabled());
    }

    ChannelView toChannelView(ChannelEntity entity) {
        return toChannelView(entity, store.loadChannelProperties(entity));
    }

    ChannelView toChannelView(ChannelEntity entity, List<PropertyItem> loaded) {
        List<PropertyItem> source = loaded == null ? List.of() : loaded;
        List<PropertyItem> properties = source;
        if (registrar != null && entity.getCapabilityType() != null) {
            properties = registrar.find(entity.getCapabilityType())
                    .map(descriptor -> CatalogConnectionSupport.redactSecrets(source, descriptor.connectionSchema()))
                    .orElse(source);
        }
        return new ChannelView(
                entity.getId(),
                entity.getCode(),
                entity.getCapabilityType(),
                properties,
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    ProductView toProductView(ProductEntity entity) {
        return new ProductView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    DeviceView toDeviceView(DeviceEntity entity) {
        return toDeviceView(entity, store.loadAllDeviceOverrides(entity), isLoaded(entity.getDeviceCode()));
    }

    DeviceView toDeviceView(
            DeviceEntity entity, Map<String, List<PropertyItem>> overrides, boolean loaded) {
        return new DeviceView(
                entity.getId(),
                entity.getDeviceCode(),
                entity.getProductId(),
                entity.getName(),
                overrides == null ? Map.of() : overrides,
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                loaded);
    }

    DeviceEndpointView toEndpointView(DeviceEndpointEntity entity) {
        return toEndpointView(entity, store.loadEndpointProperties(entity));
    }

    DeviceEndpointView toEndpointView(DeviceEndpointEntity entity, List<PropertyItem> properties) {
        return new DeviceEndpointView(
                entity.getId(),
                entity.getDeviceId(),
                entity.getChannelId(),
                properties == null ? List.of() : properties,
                entity.getCreatedAt());
    }

    boolean isLoaded(String deviceCode) {
        DriverRegistry registry = driverRegistry == null ? null : driverRegistry.getIfAvailable();
        return registry != null && registry.isRegistered(deviceCode);
    }

    private String resolveDescription(ProductFunctionEntity function) {
        if (function.getDescription() != null && !function.getDescription().isBlank()) {
            return function.getDescription();
        }
        return findTemplate(function.getCapabilityType(), function.getFunctionId())
                .map(ft -> ft.description())
                .filter(text -> text != null && !text.isBlank())
                .orElse(function.getFunctionId());
    }

    private Optional<FunctionTemplate> findTemplate(String capabilityType, String functionId) {
        if (capabilityType == null || capabilityType.isBlank() || registrar == null) {
            return Optional.empty();
        }
        return registrar.find(capabilityType).flatMap(descriptor -> descriptor.functionTemplate(functionId));
    }
}
