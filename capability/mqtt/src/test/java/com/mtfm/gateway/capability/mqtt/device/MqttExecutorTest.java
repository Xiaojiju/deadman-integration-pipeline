package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttExecutorTest {

    @Test
    void sharedChannelRefCountAndPublish() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", address("dev/A/cmd", null)));
        executor.bind(binding("B", "ch-1", address("dev/B/cmd", null)));
        assertEquals(2, transport.refCount("ch-1"));
        executor.execute(FunctionCommand.of("A", "custom.publish", Map.of("temp", 1, "tags", List.of("a", "b"))));
        assertTrue(transport.snapshot().stream().anyMatch(item ->
                item.contains("dev/A/cmd") && item.contains("\"temp\":1") && item.contains("\"tags\":[\"a\",\"b\"]")));
        executor.unbind("A");
        assertEquals(1, transport.refCount("ch-1"));
        executor.unbind("B");
        assertEquals(0, transport.refCount("ch-1"));
    }

    @Test
    void publishUsesDeliveryHintTopic() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", address("dev/A/cmd", "dev/A/door/write")));
        executor.execute(FunctionCommand.of(
                "A",
                "door.open",
                Map.of("command", "open"),
                Map.of("mqtt.publishTopic", "dev/A/door/write")));
        assertTrue(transport.snapshot().stream().anyMatch(item -> item.contains("dev/A/door/write|")));
    }

    @Test
    void fillRootValuePublishesRawString() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", address("dev/A/cmd", null)));
        executor.execute(FunctionCommand.of("A", "modbus.read", Map.of("_value", "00 00 00 00 00 01")));
        assertTrue(transport.snapshot().contains("ch-1|dev/A/cmd|00 00 00 00 00 01"));
    }

    @Test
    void emptyArgsPublishEmptyPayload() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", address("dev/A/cmd", null)));
        executor.execute(FunctionCommand.of("A", "custom.publish", Map.of()));
        assertTrue(transport.snapshot().contains("ch-1|dev/A/cmd|"));
    }

    @Test
    void connectionSchemaUsesTopicCatalog() {
        assertTrue(MqttCapability.DESCRIPTOR.addressSchema().stream()
                .anyMatch(field -> "default_pub".equals(field.name())));
        assertTrue(MqttCapability.DESCRIPTOR.functionTemplates().isEmpty());
    }

    private static DeviceEndpointBinding binding(String deviceId, String channelId, Map<String, Object> address) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                MqttCapability.TYPE,
                Attributes.from(Map.of("broker", "mqtt://localhost")),
                Attributes.from(address)
        );
    }

    private static Map<String, Object> address(String defaultPub, String doorCmd) {
        if (doorCmd == null) {
            return Map.of("default_pub", defaultPub);
        }
        return Map.of(
                "default_pub", defaultPub,
                "topics", Map.of("door_cmd", doorCmd));
    }
}
