package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.DeviceEndpointPatchRequest;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleView;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import com.mtfm.gateway.spi.property.PropertyItem;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 设备、端点与设备级覆盖。
 */
@Component
final class CatalogDeviceCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;
    private final CatalogActionRepository actions;

    CatalogDeviceCommands(CatalogStore store, CatalogFormSupport support, CatalogActionRepository actions) {
        this.store = store;
        this.support = support;
        this.actions = actions;
    }

    DeviceEntity createDevice(DeviceWriteRequest request) {
        store.findProduct(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + request.productId()));
        if (store.findDeviceByCode(request.deviceCode()).isPresent()) {
            throw new IllegalArgumentException("设备编码已存在: " + request.deviceCode());
        }
        Map<String, List<PropertyItem>> overrides = CatalogFormSupport.resolveFunctionOverrides(
                request.functionOverrides());
        DeviceEntity entity = new DeviceEntity();
        entity.setDeviceCode(request.deviceCode());
        entity.setProductId(request.productId());
        entity.setName(request.name());
        entity.setEnabled(request.enabled());
        DeviceEntity saved = store.saveDevice(entity);
        store.properties().replaceAllDeviceOverrides(saved.getId(), overrides);
        return saved;
    }

    DeviceEntity updateDevice(String deviceCode, DeviceUpdateRequest request) {
        DeviceEntity entity = support.requireDevice(deviceCode);
        String oldCode = entity.getDeviceCode();
        if (request.deviceCode() != null) {
            String nextCode = request.deviceCode().trim();
            if (nextCode.isBlank()) {
                throw new IllegalArgumentException("设备编码不能为空");
            }
            if (!nextCode.equals(oldCode)) {
                store.findDeviceByCode(nextCode).ifPresent(other -> {
                    if (!other.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException("设备编码已存在: " + nextCode);
                    }
                });
                entity.setDeviceCode(nextCode);
            }
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.functionOverrides() != null) {
            Map<String, List<PropertyItem>> overrides = CatalogFormSupport.resolveFunctionOverrides(
                    request.functionOverrides());
            store.properties().replaceAllDeviceOverrides(entity.getId(), overrides);
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        DeviceEntity saved = store.updateDevice(entity);
        if (!saved.getDeviceCode().equals(oldCode) && actions != null) {
            actions.retargetDeviceCode(oldCode, saved.getDeviceCode());
        }
        applyEndpointPatches(saved, request.endpoints());
        return saved;
    }

    private void applyEndpointPatches(DeviceEntity device, List<DeviceEndpointPatchRequest> patches) {
        if (patches == null || patches.isEmpty()) {
            return;
        }
        for (DeviceEndpointPatchRequest patch : patches) {
            if (patch == null || patch.id() == null || patch.id().isBlank()) {
                throw new IllegalArgumentException("端点 id 不能为空");
            }
            DeviceEndpointEntity endpoint = store.findEndpoint(patch.id())
                    .orElseThrow(() -> new IllegalArgumentException("端点不存在: " + patch.id()));
            if (!device.getId().equals(endpoint.getDeviceId())) {
                throw new IllegalArgumentException("端点不属于该设备: " + patch.id());
            }
            if (patch.properties() != null) {
                updateEndpoint(patch.id(), new DeviceEndpointWriteRequest(endpoint.getChannelId(), patch.properties()));
            }
        }
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
            if (request.properties() != null) {
                List<PropertyItem> items = CatalogFormSupport.resolveProperties(request.properties());
                CapabilityDescriptor descriptor = support.requireCapability(channel.getCapabilityType());
                CatalogConnectionSupport.validateAddress(descriptor, items);
                store.properties().replaceEndpointProperties(entity.getId(), items);
            }
        }
        return store.updateEndpoint(entity);
    }

    DeviceEntity registerDevice(DeviceRegisterRequest request) {
        DeviceEntity device = createDevice(new DeviceWriteRequest(
                request.deviceCode(),
                request.productId(),
                request.name(),
                request.functionOverrides(),
                request.enabled()));
        if (request.endpoints() != null) {
            for (DeviceEndpointWriteRequest endpoint : request.endpoints()) {
                createEndpoint(device.getDeviceCode(), endpoint);
            }
        }
        return device;
    }

    DeviceEndpointEntity createEndpoint(String deviceCodeOrId, DeviceEndpointWriteRequest request) {
        DeviceEntity device = support.requireDevice(deviceCodeOrId);
        ChannelEntity channel = store.findChannel(request.channelId())
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + request.channelId()));
        CapabilityDescriptor descriptor = support.requireCapability(channel.getCapabilityType());
        List<PropertyItem> items = CatalogFormSupport.resolveProperties(request.properties());
        CatalogConnectionSupport.validateAddress(descriptor, items);
        DeviceEndpointEntity entity = new DeviceEndpointEntity();
        entity.setDeviceId(device.getId());
        entity.setChannelId(channel.getId());
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
        Map<String, Object> safe = support.stripLockedContractOverrides(
                overrides == null ? Map.of() : overrides);
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

    DeviceFunctionScheduleView deviceSchedule(String deviceCode, String functionId) {
        DeviceEntity device = support.requireDevice(deviceCode);
        ProductFunctionEntity function = requireProductFunction(device, functionId);
        DeviceFunctionScheduleEntity override = store.findScheduleOverride(device.getId(), functionId).orElse(null);
        return toScheduleView(function, override);
    }

    DeviceFunctionScheduleView replaceDeviceSchedule(
            String deviceCode, String functionId, DeviceFunctionScheduleWriteRequest request) {
        DeviceEntity device = support.requireDevice(deviceCode);
        ProductFunctionEntity function = requireProductFunction(device, functionId);
        Boolean enabled = request == null ? null : request.enabled();
        Long intervalMs = request == null ? null : request.intervalMs();
        if (intervalMs != null && intervalMs < DeviceScheduleRegistry.MIN_INTERVAL_MS) {
            throw new IllegalArgumentException("intervalMs 不能小于 " + DeviceScheduleRegistry.MIN_INTERVAL_MS);
        }
        if (enabled == null && intervalMs == null) {
            store.deleteScheduleOverride(device.getId(), functionId);
            return toScheduleView(function, null);
        }
        DeviceFunctionScheduleEntity entity = store.findScheduleOverride(device.getId(), functionId)
                .orElseGet(() -> {
                    DeviceFunctionScheduleEntity created = new DeviceFunctionScheduleEntity();
                    created.setDeviceId(device.getId());
                    created.setFunctionId(functionId);
                    return created;
                });
        entity.setEnabled(enabled);
        entity.setIntervalMs(intervalMs);
        store.saveScheduleOverride(entity);
        return toScheduleView(function, entity);
    }

    private ProductFunctionEntity requireProductFunction(DeviceEntity device, String functionId) {
        return store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
    }

    private static DeviceFunctionScheduleView toScheduleView(
            ProductFunctionEntity function, DeviceFunctionScheduleEntity override) {
        boolean productEnabled = Boolean.TRUE.equals(function.getScheduleEnabled());
        Long productInterval = function.getScheduleIntervalMs();
        if (override == null) {
            return new DeviceFunctionScheduleView(
                    function.getFunctionId(),
                    productEnabled,
                    productInterval,
                    productEnabled,
                    productInterval,
                    false,
                    null,
                    null);
        }
        boolean enabled = override.getEnabled() != null
                ? Boolean.TRUE.equals(override.getEnabled())
                : productEnabled;
        Long interval = override.getIntervalMs() != null
                ? override.getIntervalMs()
                : productInterval;
        return new DeviceFunctionScheduleView(
                function.getFunctionId(),
                enabled,
                interval,
                productEnabled,
                productInterval,
                true,
                override.getEnabled(),
                override.getIntervalMs());
    }
}
