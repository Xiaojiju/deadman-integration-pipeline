package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.exception.RegistryException;
import com.mtfm.gateway.spi.port.DriverRegistry;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 南向驱动、执行器与设备绑定。
 */
public final class DefaultDriverRegistry implements DriverRegistry {

    private final ConcurrentHashMap<String, Driver> drivers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FunctionExecutor> executors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deviceBindings = new ConcurrentHashMap<>();

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
    public Optional<FunctionExecutor> findExecutor(String capabilityType) {
        return Optional.ofNullable(executors.get(capabilityType));
    }

    @Override
    public Optional<Driver> findDriver(String capabilityType) {
        return Optional.ofNullable(drivers.get(capabilityType));
    }

    @Override
    public Optional<String> findCapabilityType(String deviceId) {
        return Optional.ofNullable(deviceBindings.get(deviceId));
    }
}
