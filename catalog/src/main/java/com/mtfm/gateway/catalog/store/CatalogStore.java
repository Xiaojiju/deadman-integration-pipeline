package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.dto.DeviceListQuery;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.ProductListQuery;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.spi.model.Attributes;
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
    private final CatalogProductTypeRepository productTypes;
    private final CatalogChannelRepository channels;
    private final CatalogDeviceRepository devices;
    private final CatalogEavLoader eav;
    private final CatalogPropertyRepository properties;
    private final SecretCodec secretCodec;

    public CatalogStore(
            CatalogProductRepository products,
            CatalogProductTypeRepository productTypes,
            CatalogChannelRepository channels,
            CatalogDeviceRepository devices,
            CatalogEavLoader eav,
            CatalogPropertyRepository properties,
            ObjectProvider<SecretCodec> secretCodec) {
        this.products = products;
        this.productTypes = productTypes;
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

    /**
     * 解密后的通道连接参数，供探针/在线轮询调用南向接口。
     */
    public Attributes openedConnection(ChannelEntity channel) {
        return Attributes.from(openConnectionSecrets(PropertyCodec.toMap(loadChannelProperties(channel))));
    }

    private Map<String, Object> openConnectionSecrets(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return values == null ? Map.of() : values;
        }
        Map<String, Object> opened = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                opened.put(entry.getKey(), secretCodec.open(text));
            } else {
                opened.put(entry.getKey(), value);
            }
        }
        return opened;
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

    public Optional<ProductTypeEntity> findProductType(String idOrCode) {
        return productTypes.find(idOrCode);
    }

    public List<ProductTypeEntity> listProductTypes() {
        return productTypes.list();
    }

    public ProductTypeEntity saveProductType(ProductTypeEntity entity) {
        return productTypes.insert(entity);
    }

    public ProductTypeEntity updateProductType(ProductTypeEntity entity) {
        return productTypes.update(entity);
    }

    public boolean deleteProductType(String idOrCode) {
        ProductTypeEntity type = productTypes.find(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("产品类型不存在: " + idOrCode));
        if (products.countByProductType(type.getId()) > 0) {
            throw new IllegalArgumentException("产品类型仍被产品引用，无法删除: " + type.getCode());
        }
        return productTypes.deleteById(type.getId());
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
        return pageDevices(page, size, null);
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size, DeviceListQuery query) {
        QueryWrapper<DeviceEntity> wrapper = new QueryWrapper<DeviceEntity>().orderByAsc("device_code");
        if (query != null) {
            if (notBlank(query.name())) {
                wrapper.like("name", query.name().trim());
            }
            if (notBlank(query.deviceCode())) {
                wrapper.like("device_code", query.deviceCode().trim());
            }
            applyOnlineFilter(wrapper, query.online());
            if (notBlank(query.productTypeId())) {
                String typeId = productTypes.find(query.productTypeId())
                        .map(ProductTypeEntity::getId)
                        .orElse(query.productTypeId().trim());
                List<String> productIds = products.listIdsByProductType(typeId);
                if (productIds.isEmpty()) {
                    wrapper.eq("id", "__none__");
                } else {
                    wrapper.in("product_id", productIds);
                }
            }
        }
        return devices.page(page, size, wrapper);
    }

    public List<ProductEntity> listProducts() {
        return products.list();
    }

    public PageResult<ProductEntity> pageProducts(int page, int size) {
        return pageProducts(page, size, null);
    }

    public PageResult<ProductEntity> pageProducts(int page, int size, ProductListQuery query) {
        QueryWrapper<ProductEntity> wrapper = new QueryWrapper<ProductEntity>().orderByAsc("code");
        if (query != null) {
            if (notBlank(query.name())) {
                wrapper.like("name", query.name().trim());
            }
            if (notBlank(query.code())) {
                wrapper.like("code", query.code().trim());
            }
            if (notBlank(query.productTypeId())) {
                String typeId = productTypes.find(query.productTypeId())
                        .map(ProductTypeEntity::getId)
                        .orElse(query.productTypeId().trim());
                wrapper.eq("product_type_id", typeId);
            }
        }
        return products.page(page, size, wrapper);
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

    public List<DeviceEndpointEntity> listEndpointsByChannel(String channelPk) {
        return devices.listEndpointsByChannel(channelPk);
    }

    public Optional<DeviceEndpointEntity> findEndpoint(String devicePk, String channelPk) {
        return devices.findEndpoint(devicePk, channelPk);
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

    private static void applyOnlineFilter(QueryWrapper<DeviceEntity> wrapper, String online) {
        if (!notBlank(online)) {
            return;
        }
        String value = online.trim().toLowerCase();
        if ("unknown".equals(value) || "null".equals(value)) {
            wrapper.isNull("online");
            return;
        }
        if ("true".equals(value) || "online".equals(value)) {
            wrapper.eq("online", true);
            return;
        }
        if ("false".equals(value) || "offline".equals(value)) {
            wrapper.eq("online", false);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
