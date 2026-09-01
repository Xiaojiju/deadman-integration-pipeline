package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceFieldOverridesWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceTopicOverridesWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceEndpointView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceView;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.ProductFunctionView;
import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.dto.SupportedFunctionView;
import com.mtfm.gateway.catalog.dto.SupportedSchemaView;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FormField;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 配置域 REST：能力/功能表单、产品/通道/设备增删查改、运行时 load/unload、手动下发指令。
 * <p>对外 JSON 字段一律以结构化对象返回（非 JSON 字符串）。
 */
@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogStore store;
    private final CatalogApplyService applyService;
    private final CatalogFormService forms;

    public CatalogController(CatalogStore store, CatalogApplyService applyService, CatalogFormService forms) {
        this.store = store;
        this.applyService = applyService;
        this.forms = forms;
    }

    // ——— 能力（只读） ———

    @GetMapping("/capabilities")
    public List<CapabilityDescriptor> listCapabilities() {
        return forms.listCapabilities();
    }

    @GetMapping("/capabilities/{capabilityType}")
    public CapabilityDescriptor capability(@PathVariable String capabilityType) {
        return forms.requireCapability(capabilityType);
    }

    @GetMapping("/capabilities/{capabilityType}/functions")
    public List<FunctionTemplate> capabilityFunctions(@PathVariable String capabilityType) {
        return forms.capabilityFunctions(capabilityType);
    }

    @GetMapping("/capabilities/{capabilityType}/connection-form")
    public List<FormField> connectionForm(@PathVariable String capabilityType) {
        return forms.supportedConnection(capabilityType).fields();
    }

    @GetMapping("/capabilities/{capabilityType}/address-form")
    public List<FormField> addressForm(@PathVariable String capabilityType) {
        return forms.supportedAddress(capabilityType).fields();
    }

    /** 能力连接属性 schema（对齐旧 /supported）。 */
    @GetMapping("/capabilities/{capabilityType}/supported/connection")
    public SupportedSchemaView supportedConnection(@PathVariable String capabilityType) {
        return forms.supportedConnection(capabilityType);
    }

    /** 能力端点地址属性 schema。 */
    @GetMapping("/capabilities/{capabilityType}/supported/address")
    public SupportedSchemaView supportedAddress(@PathVariable String capabilityType) {
        return forms.supportedAddress(capabilityType);
    }

    /** 能力功能模板 + 参数属性 + choices。 */
    @GetMapping("/capabilities/{capabilityType}/supported/functions")
    public List<SupportedFunctionView> supportedFunctions(@PathVariable String capabilityType) {
        return forms.supportedFunctions(capabilityType);
    }

    /** 单功能完整表单结构（含枚举选项）。 */
    @GetMapping("/capabilities/{capabilityType}/supported/functions/{functionId}")
    public SupportedFunctionView supportedFunction(
            @PathVariable String capabilityType, @PathVariable String functionId) {
        return forms.supportedFunction(capabilityType, functionId);
    }

    // ——— 产品 ———

    @GetMapping("/products")
    public PageResult<ProductEntity> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return store.pageProducts(page, size);
    }

    @GetMapping("/products/{productId}")
    public ProductEntity getProduct(@PathVariable String productId) {
        return store.findProduct(productId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
    }

    @PostMapping("/products")
    public ProductEntity createProduct(@RequestBody ProductWriteRequest request) {
        return forms.createProduct(request);
    }

    @PutMapping("/products/{productId}")
    public ProductEntity updateProduct(@PathVariable String productId, @RequestBody ProductWriteRequest request) {
        return forms.updateProduct(productId, request);
    }

    @DeleteMapping("/products/{productId}")
    public Map<String, Object> deleteProduct(@PathVariable String productId) {
        boolean deleted = store.deleteProduct(productId);
        return Map.of("productId", productId, "deleted", deleted);
    }

    /** 列出产品功能（含 description；optionSchema / protocolMapping 为对象）。 */
    @GetMapping("/products/{productId}/functions")
    public List<ProductFunctionView> listProductFunctions(@PathVariable String productId) {
        return forms.listProductFunctionViews(productId);
    }

    @GetMapping("/products/{productId}/function-forms")
    public List<FunctionFormView> productFunctionForms(@PathVariable String productId) {
        return forms.productFunctions(productId);
    }

    @GetMapping("/products/{productId}/functions/{functionId}")
    public FunctionFormView productFunction(@PathVariable String productId, @PathVariable String functionId) {
        return forms.productFunction(productId, functionId);
    }

    @PostMapping("/products/{productId}/functions")
    public ProductFunctionView createFunction(@PathVariable String productId,
            @RequestBody ProductFunctionWriteRequest request) {
        return forms.toProductFunctionView(forms.createFunction(productId, request));
    }

    @PostMapping("/products/{productId}/functions/import")
    public List<ProductFunctionView> importFunctions(@PathVariable String productId,
            @RequestParam String capabilityType) {
        return forms.importCapabilityFunctions(productId, capabilityType).stream()
                .map(forms::toProductFunctionView)
                .toList();
    }

    @PutMapping("/products/{productId}/functions/{functionId}")
    public ProductFunctionView updateFunction(@PathVariable String productId, @PathVariable String functionId,
            @RequestBody ProductFunctionWriteRequest request) {
        return forms.toProductFunctionView(forms.updateFunction(productId, functionId, request));
    }

    @DeleteMapping("/products/{productId}/functions/{functionId}")
    public Map<String, Object> deleteFunction(@PathVariable String productId, @PathVariable String functionId) {
        boolean deleted = store.deleteFunction(productId, functionId);
        return Map.of("productId", productId, "functionId", functionId, "deleted", deleted);
    }

    // ——— 通道 ———

    @GetMapping("/channels")
    public PageResult<ChannelView> listChannels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return forms.pageChannelViews(page, size);
    }

    @GetMapping("/channels/{channelId}")
    public ChannelView getChannel(@PathVariable String channelId) {
        return forms.toChannelView(store.findChannel(channelId)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelId)));
    }

    @PostMapping("/channels")
    public ChannelView createChannel(@RequestBody ChannelWriteRequest request) {
        return forms.toChannelView(forms.createChannel(request));
    }

    @PutMapping("/channels/{channelId}")
    public ChannelView updateChannel(@PathVariable String channelId, @RequestBody ChannelWriteRequest request) {
        return forms.toChannelView(forms.updateChannel(channelId, request));
    }

    @DeleteMapping("/channels/{channelId}")
    public Map<String, Object> deleteChannel(@PathVariable String channelId) {
        boolean deleted = store.deleteChannel(channelId);
        return Map.of("channelId", channelId, "deleted", deleted);
    }

    // ——— 设备 ———

    @GetMapping("/devices")
    public PageResult<DeviceView> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return forms.pageDeviceViews(page, size);
    }

    @GetMapping("/devices/{deviceCode}")
    public DeviceView getDevice(@PathVariable String deviceCode) {
        return forms.toDeviceView(forms.requireDevice(deviceCode));
    }

    @GetMapping("/devices/{deviceCode}/functions")
    public List<FunctionFormView> deviceFunctions(@PathVariable String deviceCode) {
        return forms.deviceFunctions(deviceCode);
    }

    @GetMapping("/devices/{deviceCode}/functions/{functionId}")
    public FunctionFormView deviceFunction(@PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceFunction(deviceCode, functionId);
    }

    @PostMapping("/devices")
    public DeviceView createDevice(@RequestBody DeviceWriteRequest request) {
        return forms.toDeviceView(forms.createDevice(request));
    }

    @PostMapping("/devices/register")
    public DeviceView registerDevice(@RequestBody DeviceRegisterRequest request) {
        return forms.toDeviceView(applyService.register(request));
    }

    @PutMapping("/devices/{deviceCode}")
    public DeviceView updateDevice(@PathVariable String deviceCode, @RequestBody DeviceUpdateRequest request) {
        return forms.toDeviceView(forms.updateDevice(deviceCode, request));
    }

    @GetMapping("/devices/{deviceCode}/endpoints")
    public List<DeviceEndpointView> listEndpoints(@PathVariable String deviceCode) {
        var device = forms.requireDevice(deviceCode);
        return store.listEndpointEntities(device.getId()).stream()
                .map(forms::toEndpointView)
                .toList();
    }

    @PostMapping("/devices/{deviceCode}/endpoints")
    public DeviceEndpointView createEndpoint(@PathVariable String deviceCode,
            @RequestBody DeviceEndpointWriteRequest request) {
        return forms.toEndpointView(forms.createEndpoint(deviceCode, request));
    }

    @PutMapping("/devices/{deviceCode}/endpoints/{endpointId}")
    public DeviceEndpointView updateEndpoint(@PathVariable String deviceCode, @PathVariable String endpointId,
            @RequestBody DeviceEndpointWriteRequest request) {
        forms.requireDevice(deviceCode);
        return forms.toEndpointView(forms.updateEndpoint(endpointId, request));
    }

    @DeleteMapping("/devices/{deviceCode}/endpoints/{endpointId}")
    public Map<String, Object> deleteEndpoint(@PathVariable String deviceCode, @PathVariable String endpointId) {
        forms.requireDevice(deviceCode);
        boolean deleted = store.deleteEndpoint(endpointId);
        return Map.of("endpointId", endpointId, "deleted", deleted);
    }

    @PostMapping("/devices/{deviceCode}/load")
    public void load(@PathVariable String deviceCode) {
        applyService.load(deviceCode);
    }

    @PostMapping("/devices/{deviceCode}/unload")
    public void unload(@PathVariable String deviceCode) {
        applyService.unload(deviceCode);
    }

    @DeleteMapping("/devices/{deviceCode}")
    public Map<String, Object> deleteDevice(@PathVariable String deviceCode) {
        boolean deleted = applyService.remove(deviceCode);
        return Map.of("deviceCode", deviceCode, "deleted", deleted);
    }

    @PostMapping("/devices/{deviceCode}/commands")
    public CompletableFuture<ExecutionResult> invoke(@PathVariable String deviceCode,
            @RequestBody DeviceCommandRequest request) {
        return applyService.invoke(deviceCode, request);
    }

    /** 设备功能级字段覆盖（如 deviceId 与平台编码不同）。 */
    @GetMapping("/devices/{deviceCode}/functions/{functionId}/field-overrides")
    public Map<String, Object> deviceFieldOverrides(
            @PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceFieldOverrides(deviceCode, functionId);
    }

    @PutMapping("/devices/{deviceCode}/functions/{functionId}/field-overrides")
    public Map<String, Object> replaceDeviceFieldOverrides(
            @PathVariable String deviceCode,
            @PathVariable String functionId,
            @RequestBody DeviceFieldOverridesWriteRequest request) {
        forms.replaceDeviceFieldOverrides(deviceCode, functionId,
                request == null ? Map.of() : request.overrides());
        return forms.deviceFieldOverrides(deviceCode, functionId);
    }

    /** 设备功能级 topic slot 覆盖。 */
    @GetMapping("/devices/{deviceCode}/functions/{functionId}/topic-overrides")
    public Map<String, String> deviceTopicOverrides(
            @PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceTopicOverrides(deviceCode, functionId);
    }

    @PutMapping("/devices/{deviceCode}/functions/{functionId}/topic-overrides")
    public Map<String, String> replaceDeviceTopicOverrides(
            @PathVariable String deviceCode,
            @PathVariable String functionId,
            @RequestBody DeviceTopicOverridesWriteRequest request) {
        forms.replaceDeviceTopicOverrides(deviceCode, functionId,
                request == null ? Map.of() : request.overrides());
        return forms.deviceTopicOverrides(deviceCode, functionId);
    }
}
