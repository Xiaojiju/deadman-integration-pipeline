package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;

import java.util.List;
import java.util.Map;

/**
 * 设备、端点与设备级覆盖。
 */
final class CatalogDeviceCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogDeviceCommands(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    DeviceEntity createDevice(DeviceWriteRequest request) {
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
        Map<String, List<PropertyItem>> overrides = CatalogFormSupport.resolveFunctionOverrides(
                request.functionOverrides(), request.optionOverrides());
        DeviceEntity entity = new DeviceEntity();
        entity.setDeviceCode(request.deviceCode());
        entity.setProductId(request.productId());
        entity.setName(request.name());
        entity.setOptionOverrides(JsonMaps.write(CatalogFormSupport.toLegacyOverrideMap(overrides)));
        entity.setEnabled(request.enabled());
        DeviceEntity saved = store.saveDevice(entity);
        store.properties().replaceAllDeviceOverrides(saved.getId(), overrides);
        return saved;
    }

    DeviceEntity updateDevice(String deviceCode, DeviceUpdateRequest request) {
        DeviceEntity entity = support.requireDevice(deviceCode);
        if (request != null) {
            if (request.name() != null) {
                entity.setName(request.name());
            }
            if (request.functionOverrides() != null || request.optionOverrides() != null) {
                Map<String, List<PropertyItem>> overrides = CatalogFormSupport.resolveFunctionOverrides(
                        request.functionOverrides(), request.optionOverrides());
                entity.setOptionOverrides(JsonMaps.write(CatalogFormSupport.toLegacyOverrideMap(overrides)));
                store.properties().replaceAllDeviceOverrides(entity.getId(), overrides);
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        }
        return store.updateDevice(entity);
    }

    DeviceEndpointEntity updateEndpoint(String endpointId, DeviceEndpointWriteRequest request) {
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
                List<PropertyItem> items = CatalogFormSupport.resolveProperties(
                        request.properties(), request.address());
                CapabilityDescriptor descriptor = support.requireCapability(channel.getCapabilityType());
                CatalogConnectionSupport.validateAddress(descriptor, items);
                entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
                store.properties().replaceEndpointProperties(entity.getId(), items);
            }
        }
        return store.updateEndpoint(entity);
    }

    DeviceEntity registerDevice(DeviceRegisterRequest request) {
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

    DeviceEndpointEntity createEndpoint(String deviceCodeOrId, DeviceEndpointWriteRequest request) {
        if (request == null || request.channelId() == null || request.channelId().isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        DeviceEntity device = support.requireDevice(deviceCodeOrId);
        ChannelEntity channel = store.findChannel(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + request.channelId()));
        CapabilityDescriptor descriptor = support.requireCapability(channel.getCapabilityType());
        List<PropertyItem> items = CatalogFormSupport.resolveProperties(request.properties(), request.address());
        CatalogConnectionSupport.validateAddress(descriptor, items);
        DeviceEndpointEntity entity = new DeviceEndpointEntity();
        entity.setDeviceId(device.getId());
        entity.setChannelId(channel.getId());
        entity.setAddress(JsonMaps.write(PropertySchemas.toValueMap(items)));
        DeviceEndpointEntity saved = store.saveEndpoint(entity);
        store.properties().replaceEndpointProperties(saved.getId(), items);
        return saved;
    }

    Map<String, Object> deviceFieldOverrides(String deviceCode, String functionId) {
        DeviceEntity device = support.requireDevice(deviceCode);
        store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return store.properties().listDeviceFieldOverrides(device.getId(), functionId);
    }

    void replaceDeviceFieldOverrides(String deviceCode, String functionId, Map<String, Object> overrides) {
        DeviceEntity device = support.requireDevice(deviceCode);
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        Map<String, Object> safe = overrides == null ? Map.of() : overrides;
        support.validateFieldOverrides(function, safe);
        store.properties().replaceDeviceFieldOverrides(device.getId(), functionId, safe);
    }

    Map<String, String> deviceTopicOverrides(String deviceCode, String functionId) {
        DeviceEntity device = support.requireDevice(deviceCode);
        store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return store.properties().listDeviceTopicOverrides(device.getId(), functionId);
    }

    void replaceDeviceTopicOverrides(String deviceCode, String functionId, Map<String, String> overrides) {
        DeviceEntity device = support.requireDevice(deviceCode);
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        Map<String, String> safe = overrides == null ? Map.of() : overrides;
        DeviceEndpointBinding endpoint = null;
        if ("MQTT".equalsIgnoreCase(function.getCapabilityType())) {
            endpoint = support.requireEndpointForFunction(device, function);
        }
        support.validateTopicOverrides(function, endpoint, safe);
        store.properties().replaceDeviceTopicOverrides(device.getId(), functionId, safe);
    }
}
