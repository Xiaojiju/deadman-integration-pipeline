package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性读取：EAV 优先；空则从旧 JSON 列懒迁移一次。写路径不再双写 JSON。
 */
@Repository
public class CatalogEavLoader {

    private final CatalogPropertyRepository properties;
    private final ConcurrentHashMap<String, Object> migrateLocks = new ConcurrentHashMap<>();

    public CatalogEavLoader(CatalogPropertyRepository properties) {
        this.properties = properties;
    }

    public Map<String, List<PropertyItem>> loadChannelProperties(List<ChannelEntity> channels) {
        if (channels == null || channels.isEmpty()) {
            return Map.of();
        }
        List<String> ids = channels.stream().map(c -> c.getId()).toList();
        Map<String, List<PropertyItem>> eav = properties.listChannelPropertiesByIds(ids);
        Map<String, List<PropertyItem>> result = new LinkedHashMap<>();
        for (ChannelEntity channel : channels) {
            List<PropertyItem> items = eav.getOrDefault(channel.getId(), List.of());
            result.put(channel.getId(), items.isEmpty() ? loadChannelProperties(channel) : items);
        }
        return result;
    }

    public List<PropertyItem> loadChannelProperties(ChannelEntity channel) {
        return migrateOnce("channel:" + channel.getId(), () -> properties.listChannelProperties(channel.getId()),
                () -> {
                    Map<String, Object> legacy = PropertyCodec.readJson(channel.getConnection());
                    if (legacy.isEmpty()) {
                        return List.of();
                    }
                    List<PropertyItem> migrated = PropertyCodec.fromMap(legacy);
                    properties.replaceChannelProperties(channel.getId(), migrated);
                    return migrated;
                });
    }

    public Map<String, List<PropertyItem>> loadEndpointProperties(List<DeviceEndpointEntity> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Map.of();
        }
        List<String> ids = endpoints.stream().map(e -> e.getId()).toList();
        Map<String, List<PropertyItem>> eav = properties.listEndpointPropertiesByIds(ids);
        Map<String, List<PropertyItem>> result = new LinkedHashMap<>();
        for (DeviceEndpointEntity endpoint : endpoints) {
            List<PropertyItem> items = eav.getOrDefault(endpoint.getId(), List.of());
            result.put(endpoint.getId(), items.isEmpty() ? loadEndpointProperties(endpoint) : items);
        }
        return result;
    }

    public List<PropertyItem> loadEndpointProperties(DeviceEndpointEntity endpoint) {
        return migrateOnce("endpoint:" + endpoint.getId(), () -> properties.listEndpointProperties(endpoint.getId()),
                () -> {
                    Map<String, Object> legacy = PropertyCodec.readJson(endpoint.getAddress());
                    if (legacy.isEmpty()) {
                        return List.of();
                    }
                    List<PropertyItem> migrated = PropertyCodec.fromMap(legacy);
                    properties.replaceEndpointProperties(endpoint.getId(), migrated);
                    return migrated;
                });
    }

    public Map<String, List<PropertyItem>> loadFunctionProperties(List<ProductFunctionEntity> functions) {
        if (functions == null || functions.isEmpty()) {
            return Map.of();
        }
        List<String> ids = functions.stream().map(f -> f.getId()).toList();
        Map<String, List<PropertyItem>> eav = properties.listFunctionPropertiesByIds(ids);
        Map<String, List<PropertyItem>> result = new LinkedHashMap<>();
        for (ProductFunctionEntity function : functions) {
            List<PropertyItem> items = eav.getOrDefault(function.getId(), List.of());
            result.put(function.getId(), items.isEmpty() ? loadFunctionProperties(function) : items);
        }
        return result;
    }

    public List<PropertyItem> loadFunctionProperties(ProductFunctionEntity function) {
        return migrateOnce("function:" + function.getId(), () -> properties.listFunctionProperties(function.getId()),
                () -> {
                    Map<String, Object> legacy = PropertyCodec.readJson(function.getOptionSchema());
                    if (legacy.isEmpty()) {
                        return List.of();
                    }
                    List<PropertyItem> migrated = PropertyCodec.fromMap(legacy);
                    properties.replaceFunctionProperties(function.getId(), migrated);
                    return migrated;
                });
    }

    public List<PropertyItem> loadDeviceOverrides(DeviceEntity device, String functionId) {
        List<PropertyItem> items = properties.listDeviceOverrides(device.getId(), functionId);
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> legacy = PropertyCodec.readJson(device.getOptionOverrides());
        if (legacy.isEmpty()) {
            return List.of();
        }
        Map<String, List<PropertyItem>> byFunction = parseLegacyOverrides(legacy);
        if (byFunction.isEmpty()) {
            return List.of();
        }
        if (properties.listAllDeviceOverrides(device.getId()).isEmpty()) {
            properties.replaceAllDeviceOverrides(device.getId(), byFunction);
        }
        return byFunction.getOrDefault(functionId, List.of());
    }

    public Map<String, List<PropertyItem>> loadAllDeviceOverrides(DeviceEntity device) {
        List<DeviceFunctionOverrideEntity> rows = properties.listAllDeviceOverrides(device.getId());
        if (!rows.isEmpty()) {
            return groupOverrideRows(rows);
        }
        Map<String, Object> legacy = PropertyCodec.readJson(device.getOptionOverrides());
        Map<String, List<PropertyItem>> byFunction = parseLegacyOverrides(legacy);
        if (!byFunction.isEmpty()) {
            properties.replaceAllDeviceOverrides(device.getId(), byFunction);
        }
        return byFunction;
    }

    public Map<String, Map<String, List<PropertyItem>>> loadAllDeviceOverrides(List<DeviceEntity> devices) {
        if (devices == null || devices.isEmpty()) {
            return Map.of();
        }
        List<String> ids = devices.stream().map(d -> d.getId()).toList();
        Map<String, List<DeviceFunctionOverrideEntity>> rowsByDevice = properties.listAllDeviceOverridesByIds(ids);
        Map<String, Map<String, List<PropertyItem>>> result = new LinkedHashMap<>();
        for (DeviceEntity device : devices) {
            List<DeviceFunctionOverrideEntity> rows = rowsByDevice.getOrDefault(device.getId(), List.of());
            if (!rows.isEmpty()) {
                result.put(device.getId(), groupOverrideRows(rows));
            } else {
                result.put(device.getId(), loadAllDeviceOverrides(device));
            }
        }
        return result;
    }

    public static Map<String, List<PropertyItem>> parseLegacyOverrides(Map<String, Object> legacy) {
        Map<String, List<PropertyItem>> byFunction = new LinkedHashMap<>();
        if (legacy == null || legacy.isEmpty()) {
            return byFunction;
        }
        boolean hasNestedMaps = legacy.values().stream().anyMatch(value -> value instanceof Map<?, ?>);
        if (!hasNestedMaps) {
            return byFunction;
        }
        for (Map.Entry<String, Object> entry : legacy.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> typed = new LinkedHashMap<>();
                nested.forEach((k, v) -> typed.put(String.valueOf(k), v));
                byFunction.put(entry.getKey(), PropertyCodec.fromMap(typed));
            }
        }
        return byFunction;
    }

    private static Map<String, List<PropertyItem>> groupOverrideRows(List<DeviceFunctionOverrideEntity> rows) {
        Map<String, List<PropertyItem>> result = new LinkedHashMap<>();
        for (DeviceFunctionOverrideEntity row : rows) {
            result.computeIfAbsent(row.getFunctionId(), key -> new ArrayList<>())
                    .add(new PropertyItem(
                            row.getAttribute(),
                            row.getAttributeValue(),
                            row.getDataType(),
                            row.getDescription()));
        }
        return result;
    }

    private List<PropertyItem> migrateOnce(
            String lockKey,
            java.util.function.Supplier<List<PropertyItem>> loadEav,
            java.util.function.Supplier<List<PropertyItem>> migrate) {
        List<PropertyItem> items = loadEav.get();
        if (!items.isEmpty()) {
            return items;
        }
        Object lock = migrateLocks.computeIfAbsent(lockKey, key -> new Object());
        synchronized (lock) {
            items = loadEav.get();
            if (!items.isEmpty()) {
                return items;
            }
            return migrate.get();
        }
    }
}
