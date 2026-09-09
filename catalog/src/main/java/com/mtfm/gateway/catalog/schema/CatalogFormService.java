package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceEndpointView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleView;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceListQuery;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceView;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.ProductFunctionView;
import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductListQuery;
import com.mtfm.gateway.catalog.dto.ProductTypeView;
import com.mtfm.gateway.catalog.dto.ProductTypeWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductView;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.dto.SupportedSchemaView;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.catalog.store.FunctionOptionBundle;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.model.SchemaForms;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 配置域门面：能力 schema、CRUD 与命令装配。写路径委托给专职协作类。
 *
 * <p>示例：{@code forms.createDevice(request)} → {@link DeviceView}
 */
@Service
public class CatalogFormService {

    /** 通道视图脱敏占位；更新时回写原值，避免把掩码存进库。 */
    static final String SECRET_MASK = CatalogConnectionSupport.SECRET_MASK;

    private final CatalogStore store;
    private final CapabilityRegistrar registrar;
    private final CatalogFormSupport support;
    private final CatalogFormViews views;
    private final CatalogChannelCommands channels;
    private final CatalogProductCommands products;
    private final CatalogProductTypeCommands productTypes;
    private final CatalogDeviceCommands devices;
    private final CatalogCommandFactory commands;

    @Autowired
    public CatalogFormService(
            CatalogStore store,
            @Autowired(required = false) CapabilityRegistrar registrar,
            ObjectProvider<DriverRegistry> driverRegistry,
            ObjectProvider<CatalogFormSupport> supportProvider,
            ObjectProvider<CatalogFormViews> viewsProvider,
            ObjectProvider<CatalogChannelCommands> channelsProvider,
            ObjectProvider<CatalogProductCommands> productsProvider,
            ObjectProvider<CatalogProductTypeCommands> productTypesProvider,
            ObjectProvider<CatalogDeviceCommands> devicesProvider,
            ObjectProvider<CatalogCommandFactory> commandsProvider) {
        this.store = store;
        this.registrar = registrar;
        this.support = first(supportProvider, () -> new CatalogFormSupport(store, registrar));
        this.views = first(viewsProvider, () -> new CatalogFormViews(store, registrar, driverRegistry));
        this.channels = first(channelsProvider, () -> new CatalogChannelCommands(store, this.support));
        this.products = first(productsProvider, () -> new CatalogProductCommands(store, this.support));
        this.productTypes = first(productTypesProvider, () -> new CatalogProductTypeCommands(store));
        this.devices = first(devicesProvider, () -> new CatalogDeviceCommands(store, this.support, null));
        this.commands = first(commandsProvider, () -> new CatalogCommandFactory(store, this.support));
    }

    /** 测试装配：写路径直接注入绑定目录。 */
    CatalogFormService(CatalogStore store, CapabilityRegistrar registrar, DeviceBindingCatalog bindings) {
        this.store = store;
        this.registrar = registrar;
        this.support = new CatalogFormSupport(store, registrar, bindings);
        this.views = new CatalogFormViews(store, registrar, null);
        this.channels = new CatalogChannelCommands(store, this.support);
        this.products = new CatalogProductCommands(store, this.support);
        this.productTypes = new CatalogProductTypeCommands(store);
        this.devices = new CatalogDeviceCommands(store, this.support, null);
        this.commands = new CatalogCommandFactory(store, this.support);
    }

    private static <T> T first(ObjectProvider<T> provider, Supplier<T> fallback) {
        if (provider == null) {
            return fallback.get();
        }
        T bean = provider.getIfAvailable();
        return bean != null ? bean : fallback.get();
    }

    public List<CapabilityDescriptor> listCapabilities() {
        return registrar == null ? List.of() : registrar.list();
    }

    public CapabilityDescriptor requireCapability(String capabilityType) {
        return support.requireCapability(capabilityType);
    }

    public List<FunctionTemplate> capabilityFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates();
    }

    public SupportedSchemaView supportedConnection(String capabilityType) {
        List<SchemaField> schema = requireCapability(capabilityType).connectionSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    public SupportedSchemaView supportedAddress(String capabilityType) {
        List<SchemaField> schema = requireCapability(capabilityType).addressSchema();
        return new SupportedSchemaView(
                SchemaForms.bind(schema, Map.of()),
                PropertySchemas.toPropertyItems(schema),
                PropertySchemas.choiceOptionsByField(schema));
    }

    public List<SupportedFunctionView> supportedFunctions(String capabilityType) {
        return requireCapability(capabilityType).functionTemplates().stream()
                .map(views::toSupportedFunction)
                .toList();
    }

    public SupportedFunctionView supportedFunction(String capabilityType, String functionId) {
        FunctionTemplate template = requireCapability(capabilityType).functionTemplate(functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能模板不存在: " + functionId));
        return views.toSupportedFunction(template);
    }

    @Transactional
    public ChannelEntity createChannel(ChannelWriteRequest request) {
        return channels.createChannel(request);
    }

    @Transactional
    public ChannelEntity updateChannel(String channelIdOrCode, ChannelWriteRequest request) {
        return channels.updateChannel(channelIdOrCode, request);
    }

    @Transactional
    public ProductEntity createProduct(ProductWriteRequest request) {
        return products.createProduct(request);
    }

    @Transactional
    public ProductEntity updateProduct(String productId, ProductWriteRequest request) {
        return products.updateProduct(productId, request);
    }

    public List<ProductTypeView> listProductTypeViews() {
        return store.listProductTypes().stream().map(views::toProductTypeView).toList();
    }

    public ProductTypeView requireProductTypeView(String idOrCode) {
        return views.toProductTypeView(store.findProductType(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("产品类型不存在: " + idOrCode)));
    }

    public ProductTypeView toProductTypeView(ProductTypeEntity entity) {
        return views.toProductTypeView(entity);
    }

    @Transactional
    public ProductTypeEntity createProductType(ProductTypeWriteRequest request) {
        return productTypes.create(request);
    }

    @Transactional
    public ProductTypeEntity updateProductType(String idOrCode, ProductTypeWriteRequest request) {
        return productTypes.update(idOrCode, request);
    }

    @Transactional
    public boolean deleteProductType(String idOrCode) {
        return productTypes.delete(idOrCode);
    }

    @Transactional
    public List<ProductFunctionEntity> importCapabilityFunctions(String productId, String capabilityType) {
        return products.importCapabilityFunctions(productId, capabilityType);
    }

    @Transactional
    public ProductFunctionEntity createFunction(String productId, ProductFunctionWriteRequest request) {
        return products.createFunction(productId, request);
    }

    @Transactional
    public ProductFunctionEntity updateFunction(String productId, String functionId,
            ProductFunctionWriteRequest request) {
        return products.updateFunction(productId, functionId, request);
    }

    @Transactional
    public DeviceEntity createDevice(DeviceWriteRequest request) {
        return devices.createDevice(request);
    }

    @Transactional
    public DeviceEntity updateDevice(String deviceCode, DeviceUpdateRequest request) {
        return devices.updateDevice(deviceCode, request);
    }

    @Transactional
    public DeviceEndpointEntity updateEndpoint(String endpointId, DeviceEndpointWriteRequest request) {
        return devices.updateEndpoint(endpointId, request);
    }

    @Transactional
    public DeviceEntity registerDevice(DeviceRegisterRequest request) {
        return devices.registerDevice(request);
    }

    @Transactional
    public DeviceEndpointEntity createEndpoint(String deviceCodeOrId, DeviceEndpointWriteRequest request) {
        return devices.createEndpoint(deviceCodeOrId, request);
    }

    public List<DeviceEntity> listDevices() {
        return store.listDevices();
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size) {
        return store.pageDevices(page, size);
    }

    public PageResult<DeviceEntity> pageDevices(int page, int size, DeviceListQuery query) {
        return store.pageDevices(page, size, query);
    }

    public DeviceEntity requireDevice(String deviceCodeOrId) {
        return support.requireDevice(deviceCodeOrId);
    }

    public FunctionCommand buildCommand(String deviceCode, DeviceCommandRequest request) {
        return commands.buildCommand(deviceCode, request);
    }

    public List<FunctionFormView> productFunctions(String productId) {
        requireProduct(productId);
        return projectFunctionForms(store.listFunctions(productId), Map.of());
    }

    public List<ProductFunctionView> listProductFunctionViews(String productId) {
        requireProduct(productId);
        List<ProductFunctionEntity> functions = store.listFunctions(productId);
        Map<String, List<PropertyItem>> props = store.loadFunctionProperties(functions);
        Map<String, FunctionOptionBundle> options = store.properties().loadFunctionOptions(
                functions.stream().map(f -> f.getId()).toList());
        return functions.stream()
                .map(function -> views.toProductFunctionView(
                        function,
                        props.getOrDefault(function.getId(), List.of()),
                        options.getOrDefault(function.getId(), FunctionOptionBundle.empty())))
                .toList();
    }

    public FunctionFormView productFunction(String productId, String functionId) {
        ProductFunctionEntity function = store.findFunction(productId, functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + productId + "/" + functionId));
        return views.toForm(function, Map.of());
    }

    public List<FunctionFormView> deviceFunctions(String deviceCode) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        return projectFunctionForms(store.listFunctions(device.getProductId()), store.loadAllDeviceOverrides(device));
    }

    public FunctionFormView deviceFunction(String deviceCode, String functionId) {
        DeviceEntity device = store.findDeviceByCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        ProductFunctionEntity function = store.findFunction(device.getProductId(), functionId)
                .orElseThrow(() -> new IllegalArgumentException("功能不存在: " + functionId));
        return views.toForm(function, store.loadAllDeviceOverrides(device));
    }

    public ProductFunctionView toProductFunctionView(ProductFunctionEntity function) {
        return views.toProductFunctionView(function);
    }

    public ProductView toProductView(ProductEntity entity) {
        return views.toProductView(entity);
    }

    public ProductView requireProductView(String productId) {
        return views.toProductView(requireProduct(productId));
    }

    public PageResult<ProductView> pageProductViews(int page, int size) {
        return pageProductViews(page, size, null);
    }

    public PageResult<ProductView> pageProductViews(int page, int size, ProductListQuery query) {
        PageResult<ProductEntity> raw = store.pageProducts(page, size, query);
        return new PageResult<>(
                raw.items().stream().map(views::toProductView).toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    public boolean deleteProduct(String productId) {
        return store.deleteProduct(productId);
    }

    public boolean deleteFunction(String productId, String functionId) {
        return store.deleteFunction(productId, functionId);
    }

    public boolean deleteChannel(String channelId) {
        return store.deleteChannel(channelId);
    }

    public boolean deleteEndpoint(String endpointId) {
        return store.deleteEndpoint(endpointId);
    }

    public ChannelView toChannelView(ChannelEntity entity) {
        return views.toChannelView(entity);
    }

    public ChannelView requireChannelView(String channelId) {
        return views.toChannelView(store.findChannel(channelId)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelId)));
    }

    public DeviceView toDeviceView(DeviceEntity entity) {
        return views.toDeviceView(entity);
    }

    public DeviceEndpointView toEndpointView(DeviceEndpointEntity entity) {
        return views.toEndpointView(entity);
    }

    public Map<String, Object> deviceFieldOverrides(String deviceCode, String functionId) {
        return devices.deviceFieldOverrides(deviceCode, functionId);
    }

    @Transactional
    public void replaceDeviceFieldOverrides(String deviceCode, String functionId, Map<String, Object> overrides) {
        devices.replaceDeviceFieldOverrides(deviceCode, functionId, overrides);
    }

    public Map<String, String> deviceTopicOverrides(String deviceCode, String functionId) {
        return devices.deviceTopicOverrides(deviceCode, functionId);
    }

    @Transactional
    public void replaceDeviceTopicOverrides(String deviceCode, String functionId, Map<String, String> overrides) {
        devices.replaceDeviceTopicOverrides(deviceCode, functionId, overrides);
    }

    public DeviceFunctionScheduleView deviceSchedule(String deviceCode, String functionId) {
        return devices.deviceSchedule(deviceCode, functionId);
    }

    @Transactional
    public DeviceFunctionScheduleView replaceDeviceSchedule(
            String deviceCode, String functionId, DeviceFunctionScheduleWriteRequest request) {
        return devices.replaceDeviceSchedule(deviceCode, functionId, request);
    }

    public List<DeviceEndpointView> deviceEndpoints(String deviceCode) {
        DeviceEntity device = requireDevice(deviceCode);
        List<DeviceEndpointEntity> rows = store.listEndpointEntities(device.getId());
        Map<String, List<PropertyItem>> props = store.loadEndpointProperties(rows);
        return rows.stream()
                .map(row -> views.toEndpointView(row, props.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    public PageResult<ChannelView> pageChannelViews(int page, int size) {
        PageResult<ChannelEntity> raw = store.pageChannels(page, size);
        Map<String, List<PropertyItem>> props = store.loadChannelProperties(raw.items());
        return new PageResult<>(
                raw.items().stream()
                        .map(item -> views.toChannelView(item, props.getOrDefault(item.getId(), List.of())))
                        .toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    public PageResult<DeviceView> pageDeviceViews(int page, int size) {
        return pageDeviceViews(page, size, null);
    }

    public PageResult<DeviceView> pageDeviceViews(int page, int size, DeviceListQuery query) {
        PageResult<DeviceEntity> raw = store.pageDevices(page, size, query);
        Map<String, Map<String, List<PropertyItem>>> overrides = store.loadAllDeviceOverrides(raw.items());
        return new PageResult<>(
                raw.items().stream()
                        .map(item -> views.toDeviceView(
                                item,
                                overrides.getOrDefault(item.getId(), Map.of()),
                                views.isLoaded(item.getDeviceCode())))
                        .toList(),
                raw.total(),
                raw.page(),
                raw.size(),
                raw.totalPages());
    }

    private ProductEntity requireProduct(String productId) {
        return store.findProduct(productId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
    }

    private List<FunctionFormView> projectFunctionForms(
            List<ProductFunctionEntity> functions, Map<String, List<PropertyItem>> overrides) {
        Map<String, List<PropertyItem>> props = store.loadFunctionProperties(functions);
        Map<String, FunctionOptionBundle> options = store.properties().loadFunctionOptions(
                functions.stream().map(f -> f.getId()).toList());
        return functions.stream()
                .map(function -> views.toForm(
                        function,
                        overrides,
                        props.getOrDefault(function.getId(), List.of()),
                        options.getOrDefault(function.getId(), FunctionOptionBundle.empty())))
                .toList();
    }
}
