package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 属性读取：只认 EAV，不再回退 JSON 兼容列。
 */
@Repository
public class CatalogEavLoader {

    private final CatalogPropertyRepository properties;

    public CatalogEavLoader(CatalogPropertyRepository properties) {
        this.properties = properties;
    }

    public Map<String, List<PropertyItem>> loadChannelProperties(List<ChannelEntity> channels) {
        if (channels == null || channels.isEmpty()) {
            return Map.of();
        }
        List<String> ids = channels.stream().map(channel -> channel.getId()).collect(Collectors.toList());
        return properties.listChannelPropertiesByIds(ids);
    }

    public List<PropertyItem> loadChannelProperties(ChannelEntity channel) {
        return properties.listChannelProperties(channel.getId());
    }

    public Map<String, List<PropertyItem>> loadEndpointProperties(List<DeviceEndpointEntity> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Map.of();
        }
        List<String> ids = endpoints.stream().map(endpoint -> endpoint.getId()).collect(Collectors.toList());
        return properties.listEndpointPropertiesByIds(ids);
    }

    public List<PropertyItem> loadEndpointProperties(DeviceEndpointEntity endpoint) {
        return properties.listEndpointProperties(endpoint.getId());
    }

    public Map<String, List<PropertyItem>> loadFunctionProperties(List<ProductFunctionEntity> functions) {
        if (functions == null || functions.isEmpty()) {
            return Map.of();
        }
        List<String> ids = functions.stream().map(function -> function.getId()).collect(Collectors.toList());
        return properties.listFunctionPropertiesByIds(ids);
    }

    public List<PropertyItem> loadFunctionProperties(ProductFunctionEntity function) {
        return properties.listFunctionProperties(function.getId());
    }

    public List<PropertyItem> loadDeviceOverrides(DeviceEntity device, String functionId) {
        return properties.listDeviceOverrides(device.getId(), functionId);
    }

    public Map<String, List<PropertyItem>> loadAllDeviceOverrides(DeviceEntity device) {
        return groupOverrideRows(properties.listAllDeviceOverrides(device.getId()));
    }

    public Map<String, Map<String, List<PropertyItem>>> loadAllDeviceOverrides(List<DeviceEntity> devices) {
        if (devices == null || devices.isEmpty()) {
            return Map.of();
        }
        List<String> ids = devices.stream().map(device -> device.getId()).collect(Collectors.toList());
        Map<String, List<DeviceFunctionOverrideEntity>> rowsByDevice = properties.listAllDeviceOverridesByIds(ids);
        Map<String, Map<String, List<PropertyItem>>> result = new LinkedHashMap<>();
        for (DeviceEntity device : devices) {
            result.put(device.getId(), groupOverrideRows(rowsByDevice.getOrDefault(device.getId(), List.of())));
        }
        return result;
    }

    private static Map<String, List<PropertyItem>> groupOverrideRows(List<DeviceFunctionOverrideEntity> rows) {
        Map<String, List<PropertyItem>> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
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
}
