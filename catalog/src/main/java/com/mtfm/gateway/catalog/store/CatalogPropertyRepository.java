package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 属性仓储门面：按通道 / 端点 / 功能 / 设备拆分实现，对外保持原有 API。
 */
@Repository
public class CatalogPropertyRepository {

    private final CatalogChannelPropertyRepository channels;
    private final CatalogEndpointPropertyRepository endpoints;
    private final CatalogFunctionPropertyRepository functions;
    private final CatalogFunctionOptionRepository options;
    private final CatalogDeviceOverrideRepository devices;

    public CatalogPropertyRepository(
            CatalogChannelPropertyRepository channels,
            CatalogEndpointPropertyRepository endpoints,
            CatalogFunctionPropertyRepository functions,
            CatalogFunctionOptionRepository options,
            CatalogDeviceOverrideRepository devices) {
        this.channels = channels;
        this.endpoints = endpoints;
        this.functions = functions;
        this.options = options;
        this.devices = devices;
    }

    // ——— Channel ———

    public List<PropertyItem> listChannelProperties(String channelId) {
        return channels.list(channelId);
    }

    public Map<String, List<PropertyItem>> listChannelPropertiesByIds(Collection<String> channelIds) {
        return channels.listByChannelIds(channelIds);
    }

    public void replaceChannelProperties(String channelId, List<PropertyItem> items) {
        channels.replace(channelId, items);
    }

    public void deleteChannelProperties(String channelId) {
        channels.delete(channelId);
    }

    // ——— Endpoint ———

    public List<PropertyItem> listEndpointProperties(String endpointId) {
        return endpoints.list(endpointId);
    }

    public Map<String, List<PropertyItem>> listEndpointPropertiesByIds(Collection<String> endpointIds) {
        return endpoints.listByEndpointIds(endpointIds);
    }

    public void replaceEndpointProperties(String endpointId, List<PropertyItem> items) {
        endpoints.replace(endpointId, items);
    }

    public void deleteEndpointProperties(String endpointId) {
        endpoints.delete(endpointId);
    }

    // ——— Function property ———

    public List<PropertyItem> listFunctionProperties(String productFunctionId) {
        return functions.list(productFunctionId);
    }

    public Map<String, List<PropertyItem>> listFunctionPropertiesByIds(Collection<String> productFunctionIds) {
        return functions.listByFunctionIds(productFunctionIds);
    }

    public void replaceFunctionProperties(String productFunctionId, List<PropertyItem> items) {
        functions.replace(productFunctionId, items);
    }

    public void deleteFunctionProperties(String productFunctionId) {
        functions.delete(productFunctionId);
    }

    // ——— Device override ———

    public List<PropertyItem> listDeviceOverrides(String deviceId, String functionId) {
        return devices.listFunctionOverrides(deviceId, functionId);
    }

    public List<DeviceFunctionOverrideEntity> listAllDeviceOverrides(String deviceId) {
        return devices.listAllFunctionOverrides(deviceId);
    }

    public Map<String, List<DeviceFunctionOverrideEntity>> listAllDeviceOverridesByIds(Collection<String> deviceIds) {
        return devices.listAllFunctionOverridesByDeviceIds(deviceIds);
    }

    public void replaceDeviceOverrides(String deviceId, String functionId, List<PropertyItem> items) {
        devices.replaceFunctionOverrides(deviceId, functionId, items);
    }

    public void replaceAllDeviceOverrides(String deviceId, Map<String, List<PropertyItem>> byFunction) {
        devices.replaceAllFunctionOverrides(deviceId, byFunction);
    }

    public void deleteDeviceOverrides(String deviceId) {
        devices.deleteAll(deviceId);
    }

    public Map<String, Object> listDeviceFieldOverrides(String deviceId, String functionId) {
        return devices.listFieldOverrides(deviceId, functionId);
    }

    public Map<String, String> listDeviceTopicOverrides(String deviceId, String functionId) {
        return devices.listTopicOverrides(deviceId, functionId);
    }

    public Map<String, Map<String, String>> listDeviceTopicOverridesByDevice(String deviceId) {
        return devices.listTopicOverridesByDevice(deviceId);
    }

    public void replaceDeviceFieldOverrides(String deviceId, String functionId, Map<String, Object> overrides) {
        devices.replaceFieldOverrides(deviceId, functionId, overrides);
    }

    public void replaceDeviceTopicOverrides(String deviceId, String functionId, Map<String, String> overrides) {
        devices.replaceTopicOverrides(deviceId, functionId, overrides);
    }

    // ——— Write / Read options ———

    public List<ValueOption> listWriteValueOptions(String productFunctionId) {
        return options.listWriteValueOptions(productFunctionId);
    }

    public List<WriteFieldOption> listWriteFields(String productFunctionId) {
        return options.listWriteFields(productFunctionId);
    }

    public List<WriteFieldOption> listReadFields(String productFunctionId) {
        return options.listReadFields(productFunctionId);
    }

    public List<ValueOption> listReadValueOptions(String productFunctionId) {
        return options.listReadValueOptions(productFunctionId);
    }

    public FunctionOptionBundle loadFunctionOptions(String productFunctionId) {
        return options.loadBundle(productFunctionId);
    }

    public Map<String, FunctionOptionBundle> loadFunctionOptions(Collection<String> productFunctionIds) {
        return options.loadBundles(productFunctionIds);
    }

    public void replaceWriteOptions(
            String productFunctionId,
            ValueAccessType accessType,
            List<ValueOption> valueOptions,
            List<WriteFieldOption> writeFields) {
        options.replaceWriteOptions(productFunctionId, accessType, valueOptions, writeFields);
    }

    public void replaceReadFields(String productFunctionId, List<WriteFieldOption> readFieldsList) {
        options.replaceReadFields(productFunctionId, readFieldsList);
    }

    public void replaceReadValueOptions(String productFunctionId, List<ValueOption> options) {
        this.options.replaceReadValueOptions(productFunctionId, options);
    }

    public void deleteWriteOptions(String productFunctionId) {
        options.deleteWriteOptions(productFunctionId);
    }

    public void deleteReadValueOptions(String productFunctionId) {
        options.deleteReadValueOptions(productFunctionId);
    }

    public void deleteReadFields(String productFunctionId) {
        options.deleteReadFields(productFunctionId);
    }

    public void deleteAllForProductFunction(String productFunctionId) {
        deleteFunctionProperties(productFunctionId);
        options.deleteAll(productFunctionId);
    }
}
