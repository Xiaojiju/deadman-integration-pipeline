package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.dto.ActionGroupExecutionView;
import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.dto.DeviceLoadBatchView;
import com.mtfm.gateway.catalog.dto.DeviceLoadItemView;
import com.mtfm.gateway.catalog.dto.DeviceRegisterRequest;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import com.mtfm.gateway.spi.port.DriverRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 设备运行时加载/卸载。单设备下发见 {@link CatalogCommandInvoker}，动作组见 {@link ActionGroupExecutor}。
 *
 * <p>示例：{@code applyService.load("pump-01")}
 */
@Service
public class CatalogApplyService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogApplyService.class);

    private final CatalogStore store;
    private final DeviceBindingCatalog bindings;
    private final CatalogFormService forms;
    private final ObjectProvider<DeviceScheduleRegistry> schedules;
    private final ActionGroupExecutor actionExecutor;
    private final CatalogCommandInvoker invoker;
    private DriverRegistry registry;

    @Autowired
    public CatalogApplyService(
            CatalogStore store,
            DeviceBindingCatalog bindings,
            CatalogFormService forms,
            CatalogCommandInvoker invoker,
            @Autowired(required = false) DriverRegistry driverRegistry,
            ObjectProvider<DeviceScheduleRegistry> schedules,
            @Autowired(required = false) ActionGroupExecutor actionExecutor) {
        this.store = store;
        this.bindings = bindings;
        this.forms = forms;
        this.invoker = invoker;
        this.registry = driverRegistry;
        this.schedules = schedules;
        this.actionExecutor = actionExecutor;
    }

    /** 测试用：未装配命令/表单时只测 load/unload。 */
    CatalogApplyService(CatalogStore store, DeviceBindingCatalog bindings, DeviceScheduleRegistry schedules) {
        this.store = store;
        this.bindings = bindings;
        this.forms = null;
        this.invoker = null;
        this.registry = null;
        this.schedules = new ObjectProvider<>() {
            @Override
            public DeviceScheduleRegistry getObject() {
                return schedules;
            }

            @Override
            public DeviceScheduleRegistry getObject(Object... args) {
                return schedules;
            }

            @Override
            public DeviceScheduleRegistry getIfAvailable() {
                return schedules;
            }

            @Override
            public DeviceScheduleRegistry getIfUnique() {
                return schedules;
            }
        };
        this.actionExecutor = null;
    }

    public void attach(DriverRegistry registry) {
        this.registry = registry;
    }

    /**
     * 一次性登记设备：落库 + 可选 load。
     */
    public DeviceEntity register(DeviceRegisterRequest request) {
        if (forms == null) {
            throw new IllegalStateException("配置门面尚未装配");
        }
        DeviceEntity device = forms.registerDevice(request);
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
        List<DeviceEndpointBinding> all = bindings.findEndpoints(deviceCode);
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
        detach(deviceCode, bindings.findEndpoints(deviceCode));
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
        if (invoker == null) {
            throw new IllegalStateException("命令端口尚未装配，无法手动下发");
        }
        return invoker.invoke(deviceCode, request);
    }

    public CompletableFuture<ActionGroupExecutionView> executeActionGroup(
            String idOrCode, String source) {
        if (actionExecutor == null) {
            throw new IllegalStateException("动作组执行器尚未装配");
        }
        return actionExecutor.execute(idOrCode, source);
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

    /**
     * 批量 load。{@code deviceCodes} 为空则加载全部已启用且尚未 load 的设备。
     */
    public DeviceLoadBatchView loadBatch(List<String> deviceCodes) {
        List<DeviceEntity> targets = resolveLoadTargets(deviceCodes);
        List<DeviceLoadItemView> items = new ArrayList<>();
        int loaded = 0;
        int skipped = 0;
        int failed = 0;
        for (DeviceEntity device : targets) {
            String code = device.getDeviceCode();
            if (Boolean.FALSE.equals(device.getEnabled())) {
                skipped++;
                items.add(new DeviceLoadItemView(code, "skipped", "设备已停用"));
                continue;
            }
            if (isLoaded(code)) {
                skipped++;
                items.add(new DeviceLoadItemView(code, "skipped", "已加载"));
                continue;
            }
            try {
                load(code);
                loaded++;
                items.add(new DeviceLoadItemView(code, "loaded", null));
            } catch (RuntimeException ex) {
                failed++;
                items.add(new DeviceLoadItemView(code, "failed", ex.getMessage()));
                LOG.warn("批量 load 跳过设备 {}: {}", code, ex.getMessage());
            }
        }
        return new DeviceLoadBatchView(targets.size(), loaded, skipped, failed, List.copyOf(items));
    }

    private List<DeviceEntity> resolveLoadTargets(List<String> deviceCodes) {
        if (deviceCodes == null || deviceCodes.isEmpty()) {
            return store.listEnabledDevices();
        }
        List<DeviceEntity> found = new ArrayList<>();
        for (String code : deviceCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            found.add(store.resolveDevice(code.trim())
                    .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + code)));
        }
        return found;
    }
}
