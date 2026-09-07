package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.PipelineCommandPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 设备运行时加载/卸载与手动指令服务。
 *
 * <p>
 * 对 {@link CatalogFormService} / {@link PipelineCommandPort} 使用
 * {@link ObjectProvider}
 * 延迟获取。设备绑定走独立的 {@link DriverRegistry}（{@code DefaultRegistries}），不再依赖流水线兼五职。
 */
@Service
public class CatalogApplyService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogApplyService.class);

    private final CatalogStore store;
    private final ObjectProvider<CatalogFormService> forms;
    private final ObjectProvider<PipelineCommandPort> commandPort;
    private final ObjectProvider<DeviceScheduleRegistry> schedules;
    private final ObjectProvider<SceneListenDispatcher> sceneListen;
    private final ObjectProvider<ActionGroupExecutor> actionExecutor;
    private DriverRegistry registry;

    public CatalogApplyService(CatalogStore store,
            ObjectProvider<CatalogFormService> forms,
            ObjectProvider<PipelineCommandPort> commandPort) {
        this(store, forms, commandPort, null, null, null, null);
    }

    public CatalogApplyService(CatalogStore store,
            ObjectProvider<CatalogFormService> forms,
            ObjectProvider<PipelineCommandPort> commandPort,
            ObjectProvider<DriverRegistry> driverRegistry) {
        this(store, forms, commandPort, driverRegistry, null, null, null);
    }

    public CatalogApplyService(CatalogStore store,
            ObjectProvider<CatalogFormService> forms,
            ObjectProvider<PipelineCommandPort> commandPort,
            ObjectProvider<DriverRegistry> driverRegistry,
            ObjectProvider<DeviceScheduleRegistry> schedules) {
        this(store, forms, commandPort, driverRegistry, schedules, null, null);
    }

    @Autowired
    public CatalogApplyService(CatalogStore store,
            ObjectProvider<CatalogFormService> forms,
            ObjectProvider<PipelineCommandPort> commandPort,
            ObjectProvider<DriverRegistry> driverRegistry,
            ObjectProvider<DeviceScheduleRegistry> schedules,
            ObjectProvider<SceneListenDispatcher> sceneListen,
            ObjectProvider<ActionGroupExecutor> actionExecutor) {
        this.store = store;
        this.forms = forms;
        this.commandPort = commandPort;
        this.registry = driverRegistry == null ? null : driverRegistry.getIfAvailable();
        this.schedules = schedules;
        this.sceneListen = sceneListen;
        this.actionExecutor = actionExecutor;
    }

    public void attach(DriverRegistry registry) {
        this.registry = registry;
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
        List<DeviceEndpointBinding> all = store.findEndpoints(deviceCode);
        detach(deviceCode, all);
        if (all.isEmpty()) {
            syncSchedules(deviceCode, List.of());
            return;
        }
        try {
            List<DeviceEndpointBinding> endpoints = all.stream()
                    .filter(binding -> binding.channelEnabled())
                    .collect(Collectors.toList());
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
            registry.findExecutor(capabilityType).ifPresent(executor -> {
                for (DeviceEndpointBinding endpoint : endpoints) {
                    executor.bind(endpoint);
                }
            });
            syncSchedules(deviceCode, store.resolveSchedules(device));
        } catch (RuntimeException ex) {
            syncSchedules(deviceCode, List.of());
            throw ex;
        }
    }

    public void unload(String deviceCode) {
        if (registry == null) {
            return;
        }
        detach(deviceCode, store.findEndpoints(deviceCode));
        syncSchedules(deviceCode, List.of());
    }

    private void detach(String deviceCode, List<DeviceEndpointBinding> endpoints) {
        if (registry == null) {
            return;
        }
        Set<String> types = new LinkedHashSet<>();
        for (DeviceEndpointBinding endpoint : endpoints) {
            if (endpoint.capabilityType() != null && !endpoint.capabilityType().isBlank()) {
                types.add(endpoint.capabilityType());
            }
        }
        for (String type : types) {
            registry.findExecutor(type).ifPresent(executor -> executor.unbind(deviceCode));
        }
        registry.unregister(deviceCode);
    }

    /** 设备已 load 时按当前目录重算该设备时间轮任务。 */
    public void refreshSchedule(String deviceCode) {
        if (!isLoaded(deviceCode)) {
            return;
        }
        DeviceEntity device = store.resolveDevice(deviceCode).orElse(null);
        if (device == null || Boolean.FALSE.equals(device.getEnabled())) {
            syncSchedules(deviceCode, List.of());
            return;
        }
        syncSchedules(deviceCode, store.resolveSchedules(device));
    }

    /** 产品功能调度变更后，重算该产品下已 load 设备的时间轮。 */
    public void refreshSchedulesForProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }
        for (DeviceEntity device : store.listDevicesByProduct(productId)) {
            if (isLoaded(device.getDeviceCode())) {
                refreshSchedule(device.getDeviceCode());
            }
        }
    }

    private void syncSchedules(String deviceCode, List<DeviceScheduleRegistry.ScheduledFunction> jobs) {
        DeviceScheduleRegistry registry = schedules == null ? null : schedules.getIfAvailable();
        if (registry == null) {
            return;
        }
        if (jobs == null || jobs.isEmpty()) {
            registry.remove(deviceCode);
        } else {
            registry.replace(deviceCode, jobs);
        }
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
        return port.submit(command).whenComplete((result, error) -> {
            if (error != null || result == null || result.status() != ExecutionStatus.SUCCESS) {
                return;
            }
            SceneListenDispatcher dispatcher = sceneListen == null ? null : sceneListen.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.onCommandSuccess(
                        deviceCode, request.functionId(), command.arguments(), request.source());
            }
        });
    }

    public CompletableFuture<ActionGroupExecutionView> executeActionGroup(
            String idOrCode, String source) {
        ActionGroupExecutor executor = actionExecutor == null ? null : actionExecutor.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException("动作组执行器尚未装配");
        }
        return executor.execute(idOrCode, source);
    }

    public void reloadAll() {
        for (var device : store.listEnabledDevices()) {
            try {
                load(device.getDeviceCode());
            } catch (RuntimeException ex) {
                LOG.warn("reloadAll 跳过设备 {}: {}", device.getDeviceCode(), ex.getMessage());
            }
        }
    }
}
