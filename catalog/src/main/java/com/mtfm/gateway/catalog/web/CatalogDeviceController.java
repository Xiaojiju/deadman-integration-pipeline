package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.apply.SceneListenDispatcher;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceEndpointView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceFieldOverridesWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleView;
import com.mtfm.gateway.catalog.dto.DeviceFunctionScheduleWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.dto.DeviceTopicOverridesWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.dto.DeviceView;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.spi.model.ExecutionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
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
 * 设备配置与运行时 REST 接口，前缀 {@code /catalog/devices}。
 *
 * <p>示例：{@code GET /catalog/devices/pump-01} 查询设备；
 * {@code POST /catalog/devices/pump-01/commands} 下发指令。
 */
@Validated
@RestController
@RequestMapping("/catalog/devices")
public class CatalogDeviceController {

    private final CatalogApplyService applyService;
    private final CatalogFormService forms;
    private final SceneListenDispatcher listen;

    public CatalogDeviceController(
            CatalogApplyService applyService,
            CatalogFormService forms,
            SceneListenDispatcher listen) {
        this.applyService = applyService;
        this.forms = forms;
        this.listen = listen;
    }

    @GetMapping
    public PageResult<DeviceView> listDevices(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1") @Max(value = 100, message = "size 不能超过 100") int size) {
        return forms.pageDeviceViews(page, size);
    }

    @GetMapping("/{deviceCode}")
    public DeviceView getDevice(@PathVariable String deviceCode) {
        return forms.toDeviceView(forms.requireDevice(deviceCode));
    }

    @GetMapping("/{deviceCode}/functions")
    public List<FunctionFormView> deviceFunctions(@PathVariable String deviceCode) {
        return forms.deviceFunctions(deviceCode);
    }

    @GetMapping("/{deviceCode}/functions/{functionId}")
    public FunctionFormView deviceFunction(@PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceFunction(deviceCode, functionId);
    }

    @PostMapping
    public DeviceView createDevice(@Valid @RequestBody DeviceWriteRequest request) {
        return forms.toDeviceView(forms.createDevice(request));
    }

    @PostMapping("/register")
    public DeviceView registerDevice(@Valid @RequestBody DeviceRegisterRequest request) {
        return forms.toDeviceView(applyService.register(request));
    }

    @PutMapping("/{deviceCode}")
    public DeviceView updateDevice(@PathVariable String deviceCode, @Valid @RequestBody DeviceUpdateRequest request) {
        DeviceView before = forms.toDeviceView(forms.requireDevice(deviceCode));
        String oldCode = before.deviceCode();
        boolean wasLoaded = Boolean.TRUE.equals(before.loaded());
        boolean endpointsTouched = request.endpoints() != null && !request.endpoints().isEmpty();
        DeviceView view = forms.toDeviceView(forms.updateDevice(deviceCode, request));
        boolean codeChanged = !oldCode.equals(view.deviceCode());
        if (codeChanged) {
            listen.rebuild();
        }
        if (wasLoaded && (codeChanged || endpointsTouched)) {
            applyService.unload(oldCode);
            if (view.enabled() == null || Boolean.TRUE.equals(view.enabled())) {
                applyService.load(view.deviceCode());
                view = forms.toDeviceView(forms.requireDevice(view.deviceCode()));
            }
        }
        return view;
    }

    @GetMapping("/{deviceCode}/endpoints")
    public List<DeviceEndpointView> listEndpoints(@PathVariable String deviceCode) {
        return forms.deviceEndpoints(deviceCode);
    }

    @PostMapping("/{deviceCode}/endpoints")
    public DeviceEndpointView createEndpoint(@PathVariable String deviceCode,
            @Valid @RequestBody DeviceEndpointWriteRequest request) {
        return forms.toEndpointView(forms.createEndpoint(deviceCode, request));
    }

    @PutMapping("/{deviceCode}/endpoints/{endpointId}")
    public DeviceEndpointView updateEndpoint(@PathVariable String deviceCode, @PathVariable String endpointId,
            @Valid @RequestBody DeviceEndpointWriteRequest request) {
        forms.requireDevice(deviceCode);
        return forms.toEndpointView(forms.updateEndpoint(endpointId, request));
    }

    @DeleteMapping("/{deviceCode}/endpoints/{endpointId}")
    public Map<String, Object> deleteEndpoint(@PathVariable String deviceCode, @PathVariable String endpointId) {
        forms.requireDevice(deviceCode);
        boolean deleted = forms.deleteEndpoint(endpointId);
        return Map.of("endpointId", endpointId, "deleted", deleted);
    }

    @PostMapping("/{deviceCode}/load")
    public void load(@PathVariable String deviceCode) {
        applyService.load(deviceCode);
    }

    @PostMapping("/{deviceCode}/unload")
    public void unload(@PathVariable String deviceCode) {
        applyService.unload(deviceCode);
    }

    @DeleteMapping("/{deviceCode}")
    public Map<String, Object> deleteDevice(@PathVariable String deviceCode) {
        boolean deleted = applyService.remove(deviceCode);
        return Map.of("deviceCode", deviceCode, "deleted", deleted);
    }

    @PostMapping("/{deviceCode}/commands")
    public CompletableFuture<ExecutionResult> invoke(@PathVariable String deviceCode,
            @Valid @RequestBody DeviceCommandRequest request) {
        return applyService.invoke(deviceCode, request);
    }

    @GetMapping("/{deviceCode}/functions/{functionId}/field-overrides")
    public Map<String, Object> deviceFieldOverrides(
            @PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceFieldOverrides(deviceCode, functionId);
    }

    @PutMapping("/{deviceCode}/functions/{functionId}/field-overrides")
    public Map<String, Object> replaceDeviceFieldOverrides(
            @PathVariable String deviceCode,
            @PathVariable String functionId,
            @Valid @RequestBody DeviceFieldOverridesWriteRequest request) {
        forms.replaceDeviceFieldOverrides(deviceCode, functionId,
                request == null ? Map.of() : request.overrides());
        return forms.deviceFieldOverrides(deviceCode, functionId);
    }

    @GetMapping("/{deviceCode}/functions/{functionId}/topic-overrides")
    public Map<String, String> deviceTopicOverrides(
            @PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceTopicOverrides(deviceCode, functionId);
    }

    @PutMapping("/{deviceCode}/functions/{functionId}/topic-overrides")
    public Map<String, String> replaceDeviceTopicOverrides(
            @PathVariable String deviceCode,
            @PathVariable String functionId,
            @Valid @RequestBody DeviceTopicOverridesWriteRequest request) {
        forms.replaceDeviceTopicOverrides(deviceCode, functionId,
                request == null ? Map.of() : request.overrides());
        return forms.deviceTopicOverrides(deviceCode, functionId);
    }

    @GetMapping("/{deviceCode}/functions/{functionId}/schedule")
    public DeviceFunctionScheduleView deviceSchedule(
            @PathVariable String deviceCode, @PathVariable String functionId) {
        return forms.deviceSchedule(deviceCode, functionId);
    }

    @PutMapping("/{deviceCode}/functions/{functionId}/schedule")
    public DeviceFunctionScheduleView replaceDeviceSchedule(
            @PathVariable String deviceCode,
            @PathVariable String functionId,
            @Valid @RequestBody(required = false) DeviceFunctionScheduleWriteRequest request) {
        DeviceFunctionScheduleView view = forms.replaceDeviceSchedule(deviceCode, functionId, request);
        applyService.refreshSchedule(deviceCode);
        return view;
    }
}
