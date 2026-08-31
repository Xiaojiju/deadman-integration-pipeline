package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 南向 MQTT 执行器：共享 Channel 会话，Address 为 topic。
 *
 * <p>
 * bind 时对 {@link MqttTransport#retain(String, String)} 按 channelId 引用计数；
 * 同一 channel 下多设备共享 Broker 连接，仅 topic（Address）不同。
 * unbind 时 {@link MqttTransport#release(String)}，计数归零关闭会话。
 *
 * <pre>{@code
 * // connection={host,port} → broker URL; address={topic} → 发布目标
 * transport.retain(channelId, brokerUrl);
 * transport.publish(channelId, topic, payload);
 * transport.release(channelId);
 * }</pre>
 */
public final class MqttExecutor implements FunctionExecutor {

    private final MqttTransport transport;
    private final ConcurrentHashMap<String, Bound> bindings = new ConcurrentHashMap<>();

    /**
     * 创建 MQTT 执行器
     * 
     * @param transport 传输
     */
    public MqttExecutor(MqttTransport transport) {
        this.transport = transport;
    }

    @Override
    public String capabilityType() {
        return MqttCapability.TYPE;
    }

    @Override
    public void bind(DeviceEndpointBinding binding) {
        String broker = resolveBroker(binding);
        String topic = binding.address().get("topic").map(String::valueOf).orElse(binding.deviceId());
        Bound previous = bindings.put(binding.deviceId(), new Bound(binding.channelId(), topic));
        if (previous != null) {
            transport.release(previous.channelId());
        }
        transport.retain(binding.channelId(), broker);
    }

    @Override
    public boolean unbind(String deviceId) {
        Bound previous = bindings.remove(deviceId);
        if (previous == null) {
            return false;
        }
        transport.release(previous.channelId());
        return true;
    }

    @Override
    public ExecutionResult execute(FunctionCommand command) {
        Bound bound = bindings.get(command.deviceId());
        if (bound == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "未绑定端点: " + command.deviceId(), false));
        }
        String payload = command.arguments().get("text").map(String::valueOf).orElse("");
        transport.publish(bound.channelId(), bound.topic(), payload);
        return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                Map.of("topic", bound.topic()));
    }

    private static String resolveBroker(DeviceEndpointBinding binding) {
        return binding.connection().get("broker")
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> {
                    String host = binding.connection().get("host").map(String::valueOf).orElse("localhost");
                    String port = binding.connection().get("port").map(String::valueOf).orElse("1883");
                    return "mqtt://" + host + ":" + port;
                });
    }

    private record Bound(String channelId, String topic) {
    }
}
