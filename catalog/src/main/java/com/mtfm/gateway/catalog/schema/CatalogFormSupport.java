package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.model.DeviceBinding;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaValidator;
import com.mtfm.gateway.spi.payload.TopicCatalog;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表单写路径共享：能力查找、端点选择、覆盖校验。
 */
@Component
final class CatalogFormSupport {

    private static final Set<String> LOCKED_CONTRACT_FIELDS = Set.of("area", "dataType");

    private final CatalogStore store;
    private final CapabilityRegistrar registrar;
    private final DeviceBindingCatalog bindings;

    CatalogFormSupport(CatalogStore store, CapabilityRegistrar registrar) {
        this(store, registrar, EmptyBindings.INSTANCE);
    }

    @Autowired
    CatalogFormSupport(
            CatalogStore store,
            @Autowired(required = false) CapabilityRegistrar registrar,
            DeviceBindingCatalog bindings) {
        this.store = store;
        this.registrar = registrar;
        this.bindings = bindings == null ? EmptyBindings.INSTANCE : bindings;
    }

    CatalogStore store() {
        return store;
    }

    CapabilityRegistrar registrar() {
        return registrar;
    }

    CapabilityDescriptor requireCapability(String capabilityType) {
        if (registrar == null) {
            throw new IllegalArgumentException("能力注册中心尚未装配");
        }
        return registrar.find(capabilityType)
                .orElseThrow(() -> new IllegalArgumentException("未登记能力: " + capabilityType));
    }

    DeviceEntity requireDevice(String deviceCodeOrId) {
        return store.resolveDevice(deviceCodeOrId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCodeOrId));
    }

    DeviceEndpointBinding requireEndpointForFunction(DeviceEntity device, ProductFunctionEntity function) {
        String capability = function.getCapabilityType();
        List<DeviceEndpointBinding> matched = endpointsOf(device.getDeviceCode()).stream()
                .filter(endpoint -> capability != null && capability.equalsIgnoreCase(endpoint.capabilityType()))
                .filter(binding -> binding.channelEnabled())
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            throw new IllegalArgumentException(
                    "设备未绑定 " + capability + " 通道: " + device.getDeviceCode() + "/" + function.getFunctionId());
        }
        if (matched.size() > 1) {
            String channels = matched.stream()
                    .map(binding -> binding.channelId())
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "设备绑定了多个 " + capability + " 通道，无法确定端点: " + channels);
        }
        return matched.getFirst();
    }

    void validateCommandPayload(ProductFunctionEntity function, Map<String, Object> payload) {
        if (registrar == null || function.getCapabilityType() == null || function.getCapabilityType().isBlank()) {
            return;
        }
        Optional<CapabilityDescriptor> found = registrar.find(function.getCapabilityType());
        if (found.isEmpty()) {
            return;
        }
        CapabilityDescriptor descriptor = found.get();
        List<SchemaField> schema;
        if (descriptor.contractedParameters()) {
            schema = CatalogContractFunctionBinding.requireContractTemplate(descriptor, function.getAccessType()).parameters();
        } else {
            schema = descriptor.functionTemplate(function.getFunctionId())
                    .map(ft -> ft.parameters())
                    .orElse(List.of());
        }
        SchemaValidator.requireConstraints(schema, payload, "功能参数");
    }

    void validateFieldOverrides(ProductFunctionEntity function, Map<String, Object> overrides) {
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

    /** area / dataType 只能用产品常量，设备覆盖里出现时直接丢掉。 */
    Map<String, Object> stripLockedContractOverrides(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        overrides.forEach((key, value) -> {
            if (key == null || key.isBlank() || LOCKED_CONTRACT_FIELDS.contains(key)) {
                return;
            }
            kept.put(key, value);
        });
        return kept;
    }

    void validateTopicOverrides(
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

    static void requireEnabled(Boolean enabled, String message) {
        if (Boolean.FALSE.equals(enabled)) {
            throw new IllegalArgumentException(message);
        }
    }

    static void requireFunctionPermission(ProductFunctionEntity function) {
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

    static List<PropertyItem> resolveProperties(List<PropertyItem> properties) {
        return properties == null ? List.of() : properties;
    }

    static Map<String, List<PropertyItem>> resolveFunctionOverrides(Map<String, List<PropertyItem>> overrides) {
        return overrides == null ? Map.of() : overrides;
    }

    private List<DeviceEndpointBinding> endpointsOf(String deviceCode) {
        return bindings.findEndpoints(deviceCode);
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

    private enum EmptyBindings implements DeviceBindingCatalog {
        INSTANCE;

        @Override
        public Optional<DeviceBinding> findDevice(String deviceId) {
            return Optional.empty();
        }

        @Override
        public List<DeviceEndpointBinding> findEndpoints(String deviceId) {
            return List.of();
        }
    }
}
