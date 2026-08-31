package com.mtfm.gateway.capability.modbus;

import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 南向 Modbus 执行器：Decode / Execute / Channel 生命周期分离。
 *
 * <p><b>Channel + Address 模型</b>：Channel（connection）= host:port 共享 TCP 会话；
 * Address = slaveId 区分从站。bind 时从 {@link DeviceEndpointBinding} 提取两者，
 * 对 {@link ModbusBus#retain(ModbusChannel)} 引用计数；unbind 时 {@link ModbusBus#release(ModbusChannel)}。
 *
 * <pre>{@code
 * // bind: connection={host,port} + address={slaveId}
 * executor.bind(endpointBinding);
 * // 同一 host:port 的第二台设备 bind 不会新建 TCP，仅增加 retain 计数
 * executor.unbind(deviceId); // release，计数归零时关闭连接
 * }</pre>
 */
public final class ModbusExecutor implements FunctionExecutor {

    private final ModbusBus bus;
    private final ConcurrentHashMap<String, BoundEndpoint> endpoints = new ConcurrentHashMap<>();

    public ModbusExecutor(ModbusBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus 不能为空");
        }
        this.bus = bus;
    }

    @Override
    public String capabilityType() {
        return ModbusCapability.TYPE;
    }

    @Override
    public void bind(DeviceEndpointBinding binding) {
        String host = string(binding.connection(), "host");
        int port = intValue(binding.connection(), "port", 502);
        int unitId = intValue(binding.address(), "slaveId",
                intValue(binding.address(), "unitId", 1));
        ModbusChannel channel = new ModbusChannel(binding.channelId(), host, port);
        BoundEndpoint previous = endpoints.put(binding.deviceId(), new BoundEndpoint(channel, unitId));
        if (previous != null) {
            bus.release(previous.channel());
        }
        bus.retain(channel);
    }

    @Override
    public boolean unbind(String deviceId) {
        BoundEndpoint previous = endpoints.remove(deviceId);
        if (previous == null) {
            return false;
        }
        bus.release(previous.channel());
        return true;
    }

    public int boundDeviceCount() {
        return endpoints.size();
    }

    @Override
    public ExecutionResult execute(FunctionCommand command) {
        BoundEndpoint endpoint = endpoints.get(command.deviceId());
        if (endpoint == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "未绑定端点: " + command.deviceId(), false));
        }
        try {
            if (ModbusCapability.FN_READ.equals(command.functionId())) {
                return read(command, endpoint);
            }
            if (ModbusCapability.FN_WRITE.equals(command.functionId())) {
                return write(command, endpoint);
            }
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "不支持的功能: " + command.functionId(), false));
        } catch (RuntimeException ex) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), String.valueOf(ex.getMessage()), true));
        }
    }

    private ExecutionResult read(FunctionCommand command, BoundEndpoint endpoint) {
        Attributes args = command.arguments();
        ModbusArea area = ModbusArea.parse(stringArg(args, ModbusCapability.ARG_AREA));
        int offset = intArg(args, ModbusCapability.ARG_OFFSET, 0);
        int quantity = Math.max(1, intArg(args, ModbusCapability.ARG_QUANTITY, 1));
        ModbusDataType dataType = ModbusDataType.parse(stringArg(args, ModbusCapability.ARG_DATA_TYPE));
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            if (!area.numeric() || dataType == ModbusDataType.BOOLEAN) {
                values.add(bus.readBoolean(endpoint.channel(), endpoint.unitId(), area, offset + i));
            } else {
                values.add(bus.readNumeric(endpoint.channel(), endpoint.unitId(), area, offset + i, dataType));
            }
        }
        return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                Map.of("values", values, "unitId", endpoint.unitId()));
    }

    private ExecutionResult write(FunctionCommand command, BoundEndpoint endpoint) {
        Attributes args = command.arguments();
        ModbusArea area = ModbusArea.parse(stringArg(args, ModbusCapability.ARG_AREA));
        int offset = intArg(args, ModbusCapability.ARG_OFFSET, 0);
        Object raw = args.get(ModbusCapability.ARG_VALUE).orElse(null);
        if (raw == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "缺少 value", false));
        }
        if (!area.numeric() || raw instanceof Boolean) {
            bus.writeBoolean(endpoint.channel(), endpoint.unitId(), area, offset, Boolean.parseBoolean(String.valueOf(raw)));
        } else {
            bus.writeNumeric(endpoint.channel(), endpoint.unitId(), area, offset, ModbusDataType.INT16,
                    Integer.parseInt(String.valueOf(raw)));
        }
        return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                Map.of("written", raw, "unitId", endpoint.unitId()));
    }

    private static String string(Attributes attributes, String key) {
        return attributes.get(key).map(String::valueOf).orElse("");
    }

    private static String stringArg(Attributes attributes, String key) {
        return attributes.get(key).map(String::valueOf).orElse(null);
    }

    private static int intValue(Attributes attributes, String key, int fallback) {
        return attributes.get(key).map(value -> Integer.parseInt(String.valueOf(value))).orElse(fallback);
    }

    private static int intArg(Attributes attributes, String key, int fallback) {
        return intValue(attributes, key, fallback);
    }

    private record BoundEndpoint(ModbusChannel channel, int unitId) {
    }
}
