package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.mapper.ChannelMapper;
import com.mtfm.gateway.catalog.mapper.DeviceEndpointMapper;
import com.mtfm.gateway.catalog.mapper.DeviceMapper;
import com.mtfm.gateway.catalog.mapper.ProductFunctionMapper;
import com.mtfm.gateway.catalog.mapper.ProductMapper;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceBinding;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.option.OptionTrees;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置域持久化与 SPI 目录投影。
 *
 * <p>负责 MyBatis CRUD，并实现 {@link FunctionCatalog} / {@link DeviceBindingCatalog}。
 * 属性优先读 EAV；若 EAV 为空则从旧 JSON 列懒迁移。
 */
@Service
public class CatalogStore implements FunctionCatalog, DeviceBindingCatalog {

    private final ProductMapper products;
    private final ProductFunctionMapper functions;
    private final ChannelMapper channels;
    private final DeviceMapper devices;
    private final DeviceEndpointMapper endpoints;
    private final CatalogPropertyRepository properties;

    public CatalogStore(
            ProductMapper products,
            ProductFunctionMapper functions,
            ChannelMapper channels,
            DeviceMapper devices,
            DeviceEndpointMapper endpoints,
            CatalogPropertyRepository properties) {
        this.products = products;
        this.functions = functions;
        this.channels = channels;
        this.devices = devices;
        this.endpoints = endpoints;
        this.properties = properties;
    }

    public CatalogPropertyRepository properties() {
        return properties;
    }

    public ProductEntity saveProduct(ProductEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        products.insert(entity);
        return entity;
    }

    public ProductFunctionEntity saveFunction(ProductFunctionEntity entity) {
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getSortIndex() == null) {
            entity.setSortIndex(0);
        }
        if (entity.getWriteAccessType() == null || entity.getWriteAccessType().isBlank()) {
            entity.setWriteAccessType(ValueAccessType.VALUE.name());
        }
        functions.insert(entity);
        return entity;
    }

    public ChannelEntity saveChannel(ChannelEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        channels.insert(entity);
        return entity;
    }

    public DeviceEntity saveDevice(DeviceEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        devices.insert(entity);
        return entity;
    }

    public DeviceEndpointEntity saveEndpoint(DeviceEndpointEntity entity) {
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        endpoints.insert(entity);
        return entity;
    }

    public Optional<DeviceEntity> findDeviceByCode(String deviceCode) {
        return Optional.ofNullable(devices.selectOne(new QueryWrapper<DeviceEntity>()
                .eq("device_code", deviceCode)));
    }

    @Override
    public Optional<FunctionDef> find(String deviceId, String functionId) {
        Optional<DeviceEntity> device = findDeviceByCode(deviceId);
        if (device.isEmpty()) {
            return Optional.empty();
        }
        ProductFunctionEntity function = functions.selectOne(new QueryWrapper<ProductFunctionEntity>()
                .eq("product_id", device.get().getProductId())
                .eq("function_id", functionId));
        if (function == null) {
            return Optional.empty();
        }
        List<PropertyItem> base = loadFunctionProperties(function);
        List<PropertyItem> overrides = loadDeviceOverrides(device.get(), functionId);
        Map<String, Object> merged = new LinkedHashMap<>(PropertySchemas.toValueMap(base));
        merged.putAll(PropertySchemas.toValueMap(overrides));
        Option optionSchema = OptionTrees.fromUnknown(merged);
        ValueAccessType writeAccess = ValueAccessType.from(function.getWriteAccessType());
        List<ValueOption> writeValues = properties.listWriteValueOptions(function.getId());
        List<WriteFieldOption> writeFields = properties.listWriteFields(function.getId());
        List<WriteFieldOption> readFields = properties.listReadFields(function.getId());
        List<ValueOption> readValues = properties.listReadValueOptions(function.getId());
        return Optional.of(new FunctionDef(
                function.getFunctionId(),
                function.getAccessType(),
                function.getAccessPermission() == null ? 2 : function.getAccessPermission(),
                optionSchema,
                PropertySchemas.fromValueMap(merged),
                writeAccess,
                writeValues,
                writeFields,
                readFields,
                readValues));
    }

    @Override
    public Optional<DeviceBinding> findDevice(String deviceId) {
        List<DeviceEndpointBinding> found = findEndpoints(deviceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DeviceBinding(deviceId, found.get(0).capabilityType()));
    }

    @Override
    public List<DeviceEndpointBinding> findEndpoints(String deviceId) {
        Optional<DeviceEntity> device = findDeviceByCode(deviceId);
        if (device.isEmpty()) {
            return List.of();
        }
        List<DeviceEndpointEntity> rows = endpoints.selectList(new QueryWrapper<DeviceEndpointEntity>()
                .eq("device_id", device.get().getId()));
        return rows.stream().map(row -> {
            ChannelEntity channel = channels.selectById(row.getChannelId());
            Attributes connection = Attributes.from(PropertySchemas.toValueMap(loadChannelProperties(channel)));
            Attributes address = Attributes.from(PropertySchemas.toValueMap(loadEndpointProperties(row)));
            return new DeviceEndpointBinding(
                    device.get().getDeviceCode(),
                    channel.getCode(),
                    channel.getCapabilityType(),
                    connection,
                    address);
        }).toList();
    }

    /** 读取通道属性：EAV 优先，空则从 connection JSON 懒迁移。 */
    public List<PropertyItem> loadChannelProperties(ChannelEntity channel) {
        List<PropertyItem> items = properties.listChannelProperties(channel.getId());
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> legacy = JsonMaps.readMap(channel.getConnection());
        if (legacy.isEmpty()) {
            return List.of();
        }
        List<PropertyItem> migrated = PropertySchemas.fromValueMap(legacy);
        properties.replaceChannelProperties(channel.getId(), migrated);
        return migrated;
    }

    /** 读取端点属性：EAV 优先，空则从 address JSON 懒迁移。 */
    public List<PropertyItem> loadEndpointProperties(DeviceEndpointEntity endpoint) {
        List<PropertyItem> items = properties.listEndpointProperties(endpoint.getId());
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> legacy = JsonMaps.readMap(endpoint.getAddress());
        if (legacy.isEmpty()) {
            return List.of();
        }
        List<PropertyItem> migrated = PropertySchemas.fromValueMap(legacy);
        properties.replaceEndpointProperties(endpoint.getId(), migrated);
        return migrated;
    }

    /** 读取功能属性：EAV 优先，空则从 optionSchema JSON 懒迁移。 */
    public List<PropertyItem> loadFunctionProperties(ProductFunctionEntity function) {
        List<PropertyItem> items = properties.listFunctionProperties(function.getId());
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> legacy = JsonMaps.readMap(function.getOptionSchema());
        if (legacy.isEmpty()) {
            return List.of();
        }
        List<PropertyItem> migrated = PropertySchemas.fromValueMap(legacy);
        properties.replaceFunctionProperties(function.getId(), migrated);
        return migrated;
    }

    /**
     * 读取设备对某功能的覆盖：EAV 优先；若该 function 无 EAV，尝试从 option_overrides JSON 按 functionId 迁移。
     */
    public List<PropertyItem> loadDeviceOverrides(DeviceEntity device, String functionId) {
        List<PropertyItem> items = properties.listDeviceOverrides(device.getId(), functionId);
        if (!items.isEmpty()) {
            return items;
        }
        Map<String, Object> legacy = JsonMaps.readMap(device.getOptionOverrides());
        if (legacy.isEmpty()) {
            return List.of();
        }
        Map<String, List<PropertyItem>> byFunction = parseLegacyOverrides(legacy);
        if (byFunction.isEmpty()) {
            return List.of();
        }
        // 一次性迁移全部 function 覆盖，避免反复解析
        if (properties.listAllDeviceOverrides(device.getId()).isEmpty()) {
            properties.replaceAllDeviceOverrides(device.getId(), byFunction);
        }
        return byFunction.getOrDefault(functionId, List.of());
    }

    /** 设备全部覆盖，按 functionId 分组。 */
    public Map<String, List<PropertyItem>> loadAllDeviceOverrides(DeviceEntity device) {
        List<DeviceFunctionOverrideEntity> rows = properties.listAllDeviceOverrides(device.getId());
        if (!rows.isEmpty()) {
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
        Map<String, Object> legacy = JsonMaps.readMap(device.getOptionOverrides());
        Map<String, List<PropertyItem>> byFunction = parseLegacyOverrides(legacy);
        if (!byFunction.isEmpty()) {
            properties.replaceAllDeviceOverrides(device.getId(), byFunction);
        }
        return byFunction;
    }

    /**
     * 解析旧 option_overrides：
     * <ul>
     *   <li>{@code { "remoteControlDoor": { "a": 1 } }} → 按 functionId（值为 Map）</li>
     *   <li>{@code { "a": 1 }} 扁平标量 → 无法归属，忽略</li>
     * </ul>
     */
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
                byFunction.put(entry.getKey(), PropertySchemas.fromValueMap(typed));
            }
        }
        return byFunction;
    }

    public Optional<ProductEntity> findProduct(String productId) {
        return Optional.ofNullable(products.selectById(productId));
    }

    public List<ProductFunctionEntity> listFunctions(String productId) {
        return functions.selectList(new QueryWrapper<ProductFunctionEntity>()
                .eq("product_id", productId)
                .orderByAsc("sort_index"));
    }

    public Optional<ProductFunctionEntity> findFunction(String productId, String functionId) {
        return Optional.ofNullable(functions.selectOne(new QueryWrapper<ProductFunctionEntity>()
                .eq("product_id", productId)
                .eq("function_id", functionId)));
    }

    public Optional<ChannelEntity> findChannel(String channelId) {
        ChannelEntity byId = channels.selectById(channelId);
        if (byId != null) {
            return Optional.of(byId);
        }
        return Optional.ofNullable(channels.selectOne(new QueryWrapper<ChannelEntity>().eq("code", channelId)));
    }

    public List<DeviceEntity> listEnabledDevices() {
        return devices.selectList(new QueryWrapper<DeviceEntity>().eq("enabled", true));
    }

    public List<DeviceEntity> listDevices() {
        return devices.selectList(new QueryWrapper<DeviceEntity>().orderByAsc("device_code"));
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size) {
        Page<DeviceEntity> result = devices.selectPage(
                new Page<>(sanitizePage(page), sanitizeSize(size)),
                new QueryWrapper<DeviceEntity>().orderByAsc("device_code"));
        return PageResult.of(result);
    }

    public List<ProductEntity> listProducts() {
        return products.selectList(new QueryWrapper<ProductEntity>().orderByAsc("code"));
    }

    public PageResult<ProductEntity> pageProducts(int page, int size) {
        Page<ProductEntity> result = products.selectPage(
                new Page<>(sanitizePage(page), sanitizeSize(size)),
                new QueryWrapper<ProductEntity>().orderByAsc("code"));
        return PageResult.of(result);
    }

    public List<ChannelEntity> listChannels() {
        return channels.selectList(new QueryWrapper<ChannelEntity>().orderByAsc("code"));
    }

    public PageResult<ChannelEntity> pageChannels(int page, int size) {
        Page<ChannelEntity> result = channels.selectPage(
                new Page<>(sanitizePage(page), sanitizeSize(size)),
                new QueryWrapper<ChannelEntity>().orderByAsc("code"));
        return PageResult.of(result);
    }

    public ProductEntity updateProduct(ProductEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        products.updateById(entity);
        return entity;
    }

    public ChannelEntity updateChannel(ChannelEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        channels.updateById(entity);
        return entity;
    }

    public DeviceEntity updateDevice(DeviceEntity entity) {
        touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        devices.updateById(entity);
        return entity;
    }

    public ProductFunctionEntity updateFunction(ProductFunctionEntity entity) {
        functions.updateById(entity);
        return entity;
    }

    public long countDevicesByProduct(String productId) {
        return devices.selectCount(new QueryWrapper<DeviceEntity>().eq("product_id", productId));
    }

    public long countEndpointsByChannel(String channelPk) {
        return endpoints.selectCount(new QueryWrapper<DeviceEndpointEntity>().eq("channel_id", channelPk));
    }

    public boolean deleteProduct(String productId) {
        if (countDevicesByProduct(productId) > 0) {
            throw new IllegalArgumentException("产品仍被设备引用，无法删除: " + productId);
        }
        for (ProductFunctionEntity function : listFunctions(productId)) {
            properties.deleteAllForProductFunction(function.getId());
        }
        functions.delete(new QueryWrapper<ProductFunctionEntity>().eq("product_id", productId));
        return products.deleteById(productId) > 0;
    }

    public boolean deleteChannel(String channelIdOrCode) {
        ChannelEntity channel = findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        if (countEndpointsByChannel(channel.getId()) > 0) {
            throw new IllegalArgumentException("通道仍被设备端点引用，无法删除: " + channel.getCode());
        }
        properties.deleteChannelProperties(channel.getId());
        return channels.deleteById(channel.getId()) > 0;
    }

    public boolean deleteFunction(String productId, String functionId) {
        Optional<ProductFunctionEntity> function = findFunction(productId, functionId);
        if (function.isEmpty()) {
            return false;
        }
        properties.deleteAllForProductFunction(function.get().getId());
        return functions.deleteById(function.get().getId()) > 0;
    }

    public boolean deleteEndpoint(String endpointId) {
        properties.deleteEndpointProperties(endpointId);
        return endpoints.deleteById(endpointId) > 0;
    }

    public DeviceEndpointEntity updateEndpoint(DeviceEndpointEntity entity) {
        endpoints.updateById(entity);
        return entity;
    }

    public Optional<DeviceEndpointEntity> findEndpoint(String endpointId) {
        return Optional.ofNullable(endpoints.selectById(endpointId));
    }

    private static int sanitizePage(int page) {
        return Math.max(page, 1);
    }

    private static int sanitizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public Optional<DeviceEntity> findDeviceById(String deviceId) {
        return Optional.ofNullable(devices.selectById(deviceId));
    }

    public Optional<DeviceEntity> resolveDevice(String deviceCodeOrId) {
        Optional<DeviceEntity> byCode = findDeviceByCode(deviceCodeOrId);
        if (byCode.isPresent()) {
            return byCode;
        }
        return findDeviceById(deviceCodeOrId);
    }

    public List<DeviceEndpointEntity> listEndpointEntities(String devicePk) {
        return endpoints.selectList(new QueryWrapper<DeviceEndpointEntity>().eq("device_id", devicePk));
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
        endpoints.delete(new QueryWrapper<DeviceEndpointEntity>().eq("device_id", device.get().getId()));
        return devices.deleteById(device.get().getId()) > 0;
    }

    private static void touch(java.util.function.Consumer<Instant> created,
            java.util.function.Consumer<Instant> updated, boolean create) {
        Instant now = Instant.now();
        if (create) {
            created.accept(now);
        }
        updated.accept(now);
    }
}
