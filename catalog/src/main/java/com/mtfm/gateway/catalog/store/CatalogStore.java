package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.secret.SecretCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置域写编排门面：CRUD 与级联删除。SPI 投影见 {@link CatalogBindingCatalog} /
 * {@link CatalogFunctionProjection}。
 *
 * <p>示例：{@code store.deleteDevice("pump-01")}
 */
@Service
public class CatalogStore {

    private final CatalogProductRepository products;
    private final CatalogChannelRepository channels;
    private final CatalogDeviceRepository devices;
    private final CatalogEavLoader eav;
    private final CatalogPropertyRepository properties;
    private final SecretCodec secretCodec;

    public CatalogStore(
            CatalogProductRepository products,
            CatalogChannelRepository channels,
            CatalogDeviceRepository devices,
            CatalogEavLoader eav,
            CatalogPropertyRepository properties,
            ObjectProvider<SecretCodec> secretCodec) {
        this.products = products;
        this.channels = channels;
        this.devices = devices;
        this.eav = eav;
        this.properties = properties;
        this.secretCodec = secretCodec == null
                ? SecretCodec.identity()
                : secretCodec.getIfAvailable(SecretCodec::identity);
    }

    public CatalogPropertyRepository properties() {
        return properties;
    }

    public SecretCodec secretCodec() {
        return secretCodec;
    }

    public ProductEntity saveProduct(ProductEntity entity) {
        return products.insert(entity);
    }

    public ProductFunctionEntity saveFunction(ProductFunctionEntity entity) {
        return products.insertFunction(entity);
    }

    public ChannelEntity saveChannel(ChannelEntity entity) {
        return channels.insert(entity);
    }

    public DeviceEntity saveDevice(DeviceEntity entity) {
        return devices.insert(entity);
    }

    public DeviceEndpointEntity saveEndpoint(DeviceEndpointEntity entity) {
        return devices.insertEndpoint(entity);
    }

    public Optional<DeviceEntity> findDeviceByCode(String deviceCode) {
        return devices.findByCode(deviceCode);
    }

    public Map<String, List<PropertyItem>> loadChannelProperties(List<ChannelEntity> channels) {
        return eav.loadChannelProperties(channels);
    }

    public List<PropertyItem> loadChannelProperties(ChannelEntity channel) {
        return eav.loadChannelProperties(channel);
    }

    public Map<String, List<PropertyItem>> loadEndpointProperties(List<DeviceEndpointEntity> endpoints) {
        return eav.loadEndpointProperties(endpoints);
    }

    public List<PropertyItem> loadEndpointProperties(DeviceEndpointEntity endpoint) {
        return eav.loadEndpointProperties(endpoint);
    }

    public Map<String, List<PropertyItem>> loadFunctionProperties(List<ProductFunctionEntity> functions) {
        return eav.loadFunctionProperties(functions);
    }

    public List<PropertyItem> loadFunctionProperties(ProductFunctionEntity function) {
        return eav.loadFunctionProperties(function);
    }

    public List<PropertyItem> loadDeviceOverrides(DeviceEntity device, String functionId) {
        return eav.loadDeviceOverrides(device, functionId);
    }

    public Map<String, List<PropertyItem>> loadAllDeviceOverrides(DeviceEntity device) {
        return eav.loadAllDeviceOverrides(device);
    }

    public Map<String, Map<String, List<PropertyItem>>> loadAllDeviceOverrides(List<DeviceEntity> devices) {
        return eav.loadAllDeviceOverrides(devices);
    }

    public Optional<ProductEntity> findProduct(String productId) {
        return products.findById(productId);
    }

    public Optional<ProductEntity> findProductByCode(String code) {
        return products.findByCode(code);
    }

    public List<ProductFunctionEntity> listFunctions(String productId) {
        return products.listFunctions(productId);
    }

    public Optional<ProductFunctionEntity> findFunction(String productId, String functionId) {
        return products.findFunction(productId, functionId);
    }

    public Optional<ChannelEntity> findChannel(String channelId) {
        return channels.find(channelId);
    }

    public List<DeviceEntity> listEnabledDevices() {
        return devices.listEnabled();
    }

    public List<DeviceEntity> listDevices() {
        return devices.list();
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size) {
        return devices.page(page, size);
    }

    public List<ProductEntity> listProducts() {
        return products.list();
    }

    public PageResult<ProductEntity> pageProducts(int page, int size) {
        return products.page(page, size);
    }

    public List<ChannelEntity> listChannels() {
        return channels.list();
    }

    public PageResult<ChannelEntity> pageChannels(int page, int size) {
        return channels.page(page, size);
    }

    public ProductEntity updateProduct(ProductEntity entity) {
        return products.update(entity);
    }

    public ChannelEntity updateChannel(ChannelEntity entity) {
        return channels.update(entity);
    }

    public DeviceEntity updateDevice(DeviceEntity entity) {
        return devices.update(entity);
    }

    public ProductFunctionEntity updateFunction(ProductFunctionEntity entity) {
        return products.updateFunction(entity);
    }

    public long countDevicesByProduct(String productId) {
        return devices.countByProduct(productId);
    }

    public List<DeviceEntity> listDevicesByProduct(String productId) {
        return devices.listByProduct(productId);
    }

    public long countEndpointsByChannel(String channelPk) {
        return devices.countEndpointsByChannel(channelPk);
    }

    public boolean deleteProduct(String productId) {
        if (countDevicesByProduct(productId) > 0) {
            throw new IllegalArgumentException("产品仍被设备引用，无法删除: " + productId);
        }
        for (ProductFunctionEntity function : listFunctions(productId)) {
            properties.deleteAllForProductFunction(function.getId());
        }
        products.deleteFunctionsByProduct(productId);
        return products.deleteById(productId);
    }

    public boolean deleteChannel(String channelIdOrCode) {
        ChannelEntity channel = findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        if (countEndpointsByChannel(channel.getId()) > 0) {
            throw new IllegalArgumentException("通道仍被设备端点引用，无法删除: " + channel.getCode());
        }
        properties.deleteChannelProperties(channel.getId());
        return channels.deleteById(channel.getId());
    }

    public boolean deleteFunction(String productId, String functionId) {
        Optional<ProductFunctionEntity> function = findFunction(productId, functionId);
        if (function.isEmpty()) {
            return false;
        }
        properties.deleteAllForProductFunction(function.get().getId());
        for (DeviceEntity device : listDevicesByProduct(productId)) {
            deleteScheduleOverride(device.getId(), functionId);
        }
        return products.deleteFunctionById(function.get().getId());
    }

    public boolean deleteEndpoint(String endpointId) {
        properties.deleteEndpointProperties(endpointId);
        return devices.deleteEndpointById(endpointId);
    }

    public DeviceEndpointEntity updateEndpoint(DeviceEndpointEntity entity) {
        return devices.updateEndpoint(entity);
    }

    public Optional<DeviceEndpointEntity> findEndpoint(String endpointId) {
        return devices.findEndpoint(endpointId);
    }

    public Optional<DeviceEntity> findDeviceById(String deviceId) {
        return devices.findById(deviceId);
    }

    public Optional<DeviceEntity> resolveDevice(String deviceCodeOrId) {
        return devices.resolve(deviceCodeOrId);
    }

    public List<DeviceEndpointEntity> listEndpointEntities(String devicePk) {
        return devices.listEndpoints(devicePk);
    }

    public boolean deleteDevice(String deviceCode) {
        Optional<DeviceEntity> device = findDeviceByCode(deviceCode);
        if (device.isEmpty()) {
            return false;
        }
        for (DeviceEndpointEntity endpoint : listEndpointEntities(device.get().getId())) {
            properties.deleteEndpointProperties(endpoint.getId());
        }
        properties.deleteDeviceOverrides(device.get().getId());
        devices.deleteSchedulesByDevice(device.get().getId());
        devices.deleteEndpointsByDevice(device.get().getId());
        return devices.deleteById(device.get().getId());
    }

    public Map<String, DeviceFunctionScheduleEntity> listScheduleOverrides(String devicePk) {
        return devices.listScheduleOverrides(devicePk);
    }

    public Optional<DeviceFunctionScheduleEntity> findScheduleOverride(String devicePk, String functionId) {
        return devices.findScheduleOverride(devicePk, functionId);
    }

    public DeviceFunctionScheduleEntity saveScheduleOverride(DeviceFunctionScheduleEntity entity) {
        return devices.saveScheduleOverride(entity);
    }

    public boolean deleteScheduleOverride(String devicePk, String functionId) {
        return devices.deleteScheduleOverride(devicePk, functionId);
    }

    /**
     * 合并产品定时配置与设备覆盖，得到可登记到时间轮的任务。
     */
    public List<DeviceScheduleRegistry.ScheduledFunction> resolveSchedules(DeviceEntity device) {
        if (device == null) {
            return List.of();
        }
        return CatalogSchedules.resolve(
                listFunctions(device.getProductId()),
                listScheduleOverrides(device.getId()),
                DeviceScheduleRegistry.MIN_INTERVAL_MS);
    }
}
