package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.PipelineCommandPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备运行时加载/卸载与手动指令服务。
 *
 * <p>对 {@link CatalogFormService} / {@link PipelineCommandPort} 使用 {@link ObjectProvider}
 * 延迟获取，避免与 {@code GatewayPipeline}（兼作 CapabilityRegistrar）形成构造期循环依赖。
 */
@Service
public class CatalogApplyService {

    private final CatalogStore store;
    private final ObjectProvider<CatalogFormService> forms;
    private final ObjectProvider<PipelineCommandPort> commandPort;
    private final Map<String, FunctionExecutor> executors = new ConcurrentHashMap<>();
    private DriverRegistry registry;

    public CatalogApplyService(CatalogStore store,
            ObjectProvider<CatalogFormService> forms,
            ObjectProvider<PipelineCommandPort> commandPort) {
        this.store = store;
        this.forms = forms;
        this.commandPort = commandPort;
    }

    public void attach(DriverRegistry registry) {
        this.registry = registry;
    }

    public void registerExecutor(FunctionExecutor executor) {
        executors.put(executor.capabilityType(), executor);
    }

    /**
     * 一次性登记设备：落库 + 可选 load。
     */
    public DeviceEntity register(DeviceRegisterRequest request) {
        DeviceEntity device = forms.getObject().registerDevice(request);
        boolean shouldLoad = request.load() == null || request.load();
        if (shouldLoad) {
            load(device.getDeviceCode());
        }
        return device;
    }

    /**
     * 动态删除设备：先 unload，再删端点与设备行。
     */
    public boolean remove(String deviceCode) {
        store.resolveDevice(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        unload(deviceCode);
        return store.deleteDevice(deviceCode);
    }

    public void load(String deviceCode) {
        if (registry == null) {
            throw new IllegalStateException("尚未 attach 流水线注册表");
        }
        DeviceEntity device = store.resolveDevice(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceCode));
        if (Boolean.FALSE.equals(device.getEnabled())) {
            throw new IllegalArgumentException("设备已停用: " + deviceCode);
        }
        unload(deviceCode);
        List<DeviceEndpointBinding> all = store.findEndpoints(deviceCode);
        if (all.isEmpty()) {
            return;
        }
        List<DeviceEndpointBinding> endpoints = all.stream()
                .filter(this::channelEnabled)
                .toList();
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("设备没有已启用的通道: " + deviceCode);
        }
        Set<String> types = new LinkedHashSet<>();
        for (DeviceEndpointBinding endpoint : endpoints) {
            types.add(endpoint.capabilityType().toUpperCase());
        }
        if (types.size() > 1) {
            throw new IllegalStateException("设备绑定了多种南向能力，当前运行时一设备一协议: " + types);
        }
        String capabilityType = endpoints.getFirst().capabilityType();
        registry.register(deviceCode, capabilityType);
        FunctionExecutor executor = executors.get(capabilityType);
        if (executor != null) {
            for (DeviceEndpointBinding endpoint : endpoints) {
                executor.bind(endpoint);
            }
        }
    }

    private boolean channelEnabled(DeviceEndpointBinding endpoint) {
        ChannelEntity channel = store.findChannel(endpoint.channelId()).orElse(null);
        return channel != null && !Boolean.FALSE.equals(channel.getEnabled());
    }

    public void unload(String deviceCode) {
        if (registry == null) {
            return;
        }
        store.findDevice(deviceCode).ifPresent(binding -> {
            FunctionExecutor executor = executors.get(binding.capabilityType());
            if (executor != null) {
                executor.unbind(deviceCode);
            }
        });
        registry.unregister(deviceCode);
    }

    /** 设备是否已 load 到运行时。 */
    public boolean isLoaded(String deviceCode) {
        return registry != null && registry.isRegistered(deviceCode);
    }

    /**
     * 手动向已加载设备下发产品功能指令。
     */
    public CompletableFuture<ExecutionResult> invoke(String deviceCode, DeviceCommandRequest request) {
        PipelineCommandPort port = commandPort.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("命令端口尚未装配，无法手动下发");
        }
        FunctionCommand command = forms.getObject().buildCommand(deviceCode, request);
        if (!isLoaded(deviceCode)) {
            throw new IllegalStateException("设备未加载到运行时，请先 POST /catalog/devices/" + deviceCode + "/load");
        }
        return port.submit(command);
    }

    public void reloadAll() {
        for (var device : store.listEnabledDevices()) {
            load(device.getDeviceCode());
        }
    }
}
