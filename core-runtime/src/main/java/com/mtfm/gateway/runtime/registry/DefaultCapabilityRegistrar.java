package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.exception.RegistryException;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.port.DriverRegistry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力描述登记。驱动与执行器转给 {@link DriverRegistry}。
 */
public final class DefaultCapabilityRegistrar implements CapabilityRegistrar {

    private final ConcurrentHashMap<String, CapabilityDescriptor> descriptors = new ConcurrentHashMap<>();
    private final DriverRegistry drivers;

    public DefaultCapabilityRegistrar(DriverRegistry drivers) {
        this.drivers = drivers;
    }

    @Override
    public void register(CapabilityDescriptor descriptor, Driver driver, FunctionExecutor executor) {
        if (descriptor == null) {
            throw new RegistryException("CapabilityDescriptor 不能为空");
        }
        descriptors.put(descriptor.capabilityType(), descriptor);
        if (driver != null) {
            drivers.registerDriver(driver);
        }
        if (executor != null) {
            drivers.registerExecutor(executor);
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
}
