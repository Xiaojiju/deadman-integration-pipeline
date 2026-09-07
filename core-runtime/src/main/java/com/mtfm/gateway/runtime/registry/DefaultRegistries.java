package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.plugin.InboundPlugin;
import com.mtfm.gateway.spi.plugin.OutboundPlugin;
import com.mtfm.gateway.spi.port.DriverRegistry;
import com.mtfm.gateway.spi.port.PluginRegistry;
import com.mtfm.gateway.spi.port.PublisherRegistry;

import java.util.List;
import java.util.Optional;

/**
 * 流水线注册表门面。驱动、插件、发布器、能力描述分表存放，对外仍是一个装配入口。
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

    private final DefaultDriverRegistry drivers = new DefaultDriverRegistry();
    private final DefaultPluginRegistry plugins = new DefaultPluginRegistry();
    private final DefaultPublisherRegistry publishers = new DefaultPublisherRegistry();
    private final DefaultCapabilityRegistrar capabilities = new DefaultCapabilityRegistrar(drivers);

    @Override
    public void registerDriver(Driver driver) {
        drivers.registerDriver(driver);
    }

    @Override
    public void registerExecutor(FunctionExecutor executor) {
        drivers.registerExecutor(executor);
    }

    @Override
    public boolean register(String deviceId, String capabilityType) {
        return drivers.register(deviceId, capabilityType);
    }

    @Override
    public boolean unregister(String deviceId) {
        return drivers.unregister(deviceId);
    }

    @Override
    public boolean isRegistered(String deviceId) {
        return drivers.isRegistered(deviceId);
    }

    @Override
    public boolean register(InboundPlugin plugin) {
        return plugins.register(plugin);
    }

    @Override
    public boolean register(OutboundPlugin plugin) {
        return plugins.register(plugin);
    }

    @Override
    public boolean register(Publisher publisher) {
        return publishers.register(publisher);
    }

    @Override
    public void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor) {
        capabilities.register(descriptor, driver, executor);
    }

    @Override
    public Optional<CapabilityDescriptor> find(String capabilityType) {
        return capabilities.find(capabilityType);
    }

    @Override
    public List<CapabilityDescriptor> list() {
        return capabilities.list();
    }

    @Override
    public Optional<Driver> findDriver(String capabilityType) {
        return drivers.findDriver(capabilityType);
    }

    @Override
    public Optional<FunctionExecutor> findExecutor(String capabilityType) {
        return drivers.findExecutor(capabilityType);
    }

    @Override
    public Optional<String> findCapabilityType(String deviceId) {
        return drivers.findCapabilityType(deviceId);
    }

    @Override
    public Optional<Publisher> findPublisher(String channelHint) {
        return publishers.findPublisher(channelHint);
    }

    @Override
    public List<InboundPlugin> inboundInOrder() {
        return plugins.inboundInOrder();
    }

    @Override
    public List<OutboundPlugin> outboundInOrder() {
        return plugins.outboundInOrder();
    }
}
