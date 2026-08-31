package com.mtfm.gateway.spi.capability;

import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.FunctionCommand;

/**
 * 南向功能执行器。在核心 Execute 线程上同步调用。
 *
 * <p>禁止自行 publish；回执由 Correlate/Egress 负责。
 * 可通过 {@link #bind(DeviceEndpointBinding)} / {@link #unbind(String)} 管理设备连接。
 *
 * <p>使用示例：
 * <pre>{@code
 * public final class ModbusExecutor implements FunctionExecutor {
 *     public String capabilityType() { return "modbus-tcp"; }
 *     public ExecutionResult execute(FunctionCommand command) {
 *         int value = client.readRegister(command.deviceId(), command.arguments());
 *         return ExecutionResult.success(command.requestId(), command.deviceId(),
 *                 command.functionId(), Map.of("value", value));
 *     }
 * }
 * }</pre>
 */
public interface FunctionExecutor {

    /** 本执行器对应的能力类型标识。 */
    String capabilityType();

    /** 是否支持该命令；默认匹配 {@code capabilityType}。 */
    default boolean support(FunctionCommand command) {
        if (command.capabilityType() == null) {
            return true;
        }
        return capabilityType().equals(command.capabilityType());
    }

    /**
     * 同步执行功能命令。
     *
     * @param command 由已贴胶 COMMAND 信封物化
     * @return 执行结果，不得自行 publish
     */
    ExecutionResult execute(FunctionCommand command);

    /**
     * 按设备绑定通道与地址。同一 Channel 可被多个设备 retain。
     */
    default void bind(DeviceEndpointBinding binding) {
    }

    /**
     * 按设备解绑；通道引用计数为 0 才断开。
     */
    default boolean unbind(String deviceId) {
        return false;
    }
}
