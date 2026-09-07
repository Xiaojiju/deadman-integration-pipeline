package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;

import java.util.Optional;

/**
 * 南向能力与设备绑定登记端口。
 *
 * <p>使用示例：
 * <pre>{@code
 * registry.registerDriver(modbusDriver);
 * registry.registerExecutor(modbusExecutor);
 * registry.register("dev-001", "modbus-tcp");
 * registry.unregister("dev-001");
 * }</pre>
 */
public interface DriverRegistry {

    /** 注册南向解码驱动。 */
    void registerDriver(Driver driver);

    /** 注册南向功能执行器。 */
    void registerExecutor(FunctionExecutor executor);

    /**
     * 绑定设备与南向能力。一设备一南向协议，重复绑定不同类型失败。
     *
     * @param deviceId       设备 ID
     * @param capabilityType 能力类型
     * @return 是否绑定成功
     */
    boolean register(String deviceId, String capabilityType);

    /**
     * 解除设备绑定；在途 Execute 不中断。
     *
     * @param deviceId 设备 ID
     * @return 是否解绑成功
     */
    boolean unregister(String deviceId);

    /** 设备是否已绑定到运行时（已 load）。 */
    boolean isRegistered(String deviceId);

    /** 按能力类型查找已注册的南向执行器。 */
    Optional<FunctionExecutor> findExecutor(String capabilityType);

    /** 按能力类型查找已注册的南向驱动。 */
    Optional<Driver> findDriver(String capabilityType);

    /** 已 load 设备绑定的南向能力类型。 */
    Optional<String> findCapabilityType(String deviceId);
}
