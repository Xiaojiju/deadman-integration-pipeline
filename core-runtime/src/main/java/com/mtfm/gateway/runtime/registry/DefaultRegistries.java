package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.exception.RegistryException;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.PluginRegistry;
import com.mtfm.gateway.spi.port.PublisherRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 流水线组件注册表，实例级注入，禁止进程级 static Map。
 *
 * <p>维护以下映射关系：
 * <ul>
 *   <li>{@code capabilityType → Driver / FunctionExecutor / CapabilityDescriptor}</li>
 *   <li>{@code deviceId → capabilityType} 设备南向协议绑定</li>
 *   <li>{@code channelHint → Publisher} 北向发布通道</li>
 *   <li>按 {@code order} 排序的入站/出站插件列表</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * DefaultRegistries registries = new DefaultRegistries();
 * registries.register(ModbusCapability.DESCRIPTOR, modbusDriver, modbusExecutor);
 * registries.register(deviceId, ModbusCapability.TYPE);
 * registries.register(cloudPublisher);
 * }</pre>
 */
public final class DefaultRegistries implements DriverRegistry, PluginRegistry, PublisherRegistry, CapabilityRegistrar {

    private final ConcurrentHashMap<String, Driver> drivers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FunctionExecutor> executors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deviceBindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CapabilityDescriptor> descriptors = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InboundPlugin> inboundPlugins = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OutboundPlugin> outboundPlugins = new CopyOnWriteArrayList<>();

    @Override
    public void registerDriver(Driver driver) {
        if (driver == null || driver.capabilityType() == null || driver.capabilityType().isBlank()) {
            throw new RegistryException("Driver 或 capabilityType 不能为空");
        }
        drivers.put(driver.capabilityType(), driver);
    }

    @Override
    public void registerExecutor(FunctionExecutor executor) {
        if (executor == null || executor.capabilityType() == null || executor.capabilityType().isBlank()) {
            throw new RegistryException("Executor 或 capabilityType 不能为空");
        }
        executors.put(executor.capabilityType(), executor);
    }

    @Override
    public boolean register(String deviceId, String capabilityType) {
        if (deviceId == null || deviceId.isBlank() || capabilityType == null || capabilityType.isBlank()) {
            throw new RegistryException("deviceId / capabilityType 不能为空");
        }
        String existing = deviceBindings.putIfAbsent(deviceId, capabilityType);
        if (existing != null && !existing.equals(capabilityType)) {
            throw new RegistryException("设备已绑定其他南向协议: " + deviceId + " -> " + existing);
        }
        return existing == null;
    }

    @Override
    public boolean unregister(String deviceId) {
        return deviceBindings.remove(deviceId) != null;
    }

    @Override
    public boolean isRegistered(String deviceId) {
        return deviceId != null && deviceBindings.containsKey(deviceId);
    }

    @Override
    public boolean register(InboundPlugin plugin) {
        if (plugin == null || plugin.name() == null || plugin.name().isBlank()) {
            throw new RegistryException("入站插件名不能为空");
        }
        boolean exists = inboundPlugins.stream().anyMatch(item -> item.name().equals(plugin.name()));
        if (exists) {
            return false;
        }
        inboundPlugins.add(plugin);
        inboundPlugins.sort(Comparator.comparingInt(InboundPlugin::order));
        return true;
    }

    @Override
    public boolean register(OutboundPlugin plugin) {
        if (plugin == null || plugin.name() == null || plugin.name().isBlank()) {
            throw new RegistryException("出站插件名不能为空");
        }
        boolean exists = outboundPlugins.stream().anyMatch(item -> item.name().equals(plugin.name()));
        if (exists) {
            return false;
        }
        outboundPlugins.add(plugin);
        outboundPlugins.sort(Comparator.comparingInt(OutboundPlugin::order));
        return true;
    }

    @Override
    public boolean register(Publisher publisher) {
        if (publisher == null || publisher.channel() == null || publisher.channel().isBlank()) {
            throw new RegistryException("Publisher.channel 不能为空");
        }
        Publisher existing = publishers.putIfAbsent(publisher.channel(), publisher);
        if (existing != null && existing != publisher) {
            throw new RegistryException("同一 channelHint 只能绑一个 Publisher: " + publisher.channel());
        }
        return existing == null;
    }

    @Override
    public void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor) {
        if (descriptor == null) {
            throw new RegistryException("CapabilityDescriptor 不能为空");
        }
        descriptors.put(descriptor.capabilityType(), descriptor);
        if (driver != null) {
            registerDriver(driver);
        }
        if (executor != null) {
            registerExecutor(executor);
        }
    }

    @Override
    public Optional<CapabilityDescriptor> find(String capabilityType) {
        return Optional.ofNullable(descriptors.get(capabilityType));
    }

    @Override
    public List<CapabilityDescriptor> list() {
        return List.copyOf(descriptors.values());
    }

    public Optional<Driver> findDriver(String capabilityType) {
        return Optional.ofNullable(drivers.get(capabilityType));
    }

    @Override
    public Optional<FunctionExecutor> findExecutor(String capabilityType) {
        return Optional.ofNullable(executors.get(capabilityType));
    }

    public Optional<String> findCapabilityType(String deviceId) {
        return Optional.ofNullable(deviceBindings.get(deviceId));
    }

    public Optional<Publisher> findPublisher(String channelHint) {
        return Optional.ofNullable(publishers.get(channelHint));
    }

    public List<InboundPlugin> inboundInOrder() {
        return List.copyOf(inboundPlugins);
    }

    public List<OutboundPlugin> outboundInOrder() {
        return List.copyOf(outboundPlugins);
    }
}
