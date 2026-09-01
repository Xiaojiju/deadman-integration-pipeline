package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttExecutorTest {

    @Test
    void sharedChannelRefCountAndPublish() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", "dev/A"));
        executor.bind(binding("B", "ch-1", "dev/B"));
        assertEquals(2, transport.refCount("ch-1"));
        executor.execute(FunctionCommand.of("A", MqttCapability.FN_PUBLISH, Map.of("text", "hello")));
        assertTrue(transport.snapshot().stream().anyMatch(item -> item.contains("dev/A")));
        executor.unbind("A");
        assertEquals(1, transport.refCount("ch-1"));
        executor.unbind("B");
        assertEquals(0, transport.refCount("ch-1"));
    }

    private static DeviceEndpointBinding binding(String deviceId, String channelId, String topic) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                MqttCapability.TYPE,
                Attributes.from(Map.of("broker", "mqtt://localhost")),
                Attributes.from(Map.of("topic", topic))
        );
    }
}
