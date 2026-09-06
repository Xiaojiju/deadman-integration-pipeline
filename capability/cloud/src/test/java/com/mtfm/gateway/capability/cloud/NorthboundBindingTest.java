package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.MessageHeaders;
import com.mtfm.gateway.spi.model.MessagePriority;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.northbound.NorthboundSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorthboundBindingTest {

    @Test
    void memoryApplyStartsIngressAndFanout() {
        List<NorthboundCommand> received = new CopyOnWriteArrayList<>();
        try (CloudPublisher hub = new CloudPublisher();
                NorthboundBinding binding = new NorthboundBinding(hub, received::add)) {
            binding.apply(memorySettings());
            assertTrue(binding.status().mqttLive());
            assertFalse(binding.status().httpLive());

            NorthboundMqttSession session = binding.session();
            assertNotNull(session);
            session.publish("gw/door-1/command",
                    "{\"requestId\":\"req-live\",\"functionId\":\"fn.open\",\"arguments\":{\"lock\":\"open\"}}");
            assertEquals(1, received.size());
            assertEquals("door-1", received.getFirst().deviceId());

            if (session instanceof InMemoryNorthboundMqttSession memory) {
                hub.publish(response("req-out", "door-1"));
                assertTrue(memory.snapshot().stream().anyMatch(item -> item.startsWith("gw/door-1/response|")));
            }
        }
    }

    @Test
    void hotSwapClearsOldMemorySubscriptions() {
        List<NorthboundCommand> received = new CopyOnWriteArrayList<>();
        try (CloudPublisher hub = new CloudPublisher();
                NorthboundBinding binding = new NorthboundBinding(hub, received::add)) {
            binding.apply(memorySettings());
            NorthboundMqttSession first = binding.session();
            binding.apply(memorySettings());
            NorthboundMqttSession second = binding.session();
            assertTrue(first != second);
            first.publish("gw/door-1/command",
                    "{\"requestId\":\"stale\",\"functionId\":\"fn.open\",\"arguments\":{}}");
            assertTrue(received.isEmpty());
            second.publish("gw/door-1/command",
                    "{\"requestId\":\"fresh\",\"functionId\":\"fn.open\",\"arguments\":{}}");
            assertEquals(1, received.size());
            assertEquals("fresh", received.getFirst().requestId());
        }
    }

    @Test
    void pahoConnectFailureKeepsHttpAndDoesNotThrow() {
        try (CloudPublisher hub = new CloudPublisher();
                NorthboundBinding binding = new NorthboundBinding(hub, command -> {
                })) {
            NorthboundSettings settings = new NorthboundSettings(
                    true, NorthboundSettings.TRANSPORT_PAHO, "not-a-broker",
                    NorthboundSettings.DEFAULT_COMMAND_TOPIC,
                    NorthboundSettings.DEFAULT_RESPONSE_TOPIC,
                    NorthboundSettings.DEFAULT_TELEMETRY_TOPIC,
                    "nb-test-fail", "", "",
                    true, "http://127.0.0.1:9/hook", 200, 1);
            binding.apply(settings);
            assertFalse(binding.status().mqttLive());
            assertNotNull(binding.status().mqttError());
            assertTrue(binding.status().httpLive());
        }
    }

    @Test
    void disableClearsLiveStatus() {
        try (CloudPublisher hub = new CloudPublisher();
                NorthboundBinding binding = new NorthboundBinding(hub, command -> {
                })) {
            binding.apply(memorySettings());
            assertTrue(binding.status().mqttLive());
            binding.apply(NorthboundSettings.disabled());
            assertFalse(binding.status().mqttLive());
            assertFalse(binding.status().httpLive());
        }
    }

    private static NorthboundSettings memorySettings() {
        return new NorthboundSettings(
                true, NorthboundSettings.TRANSPORT_MEMORY, "",
                NorthboundSettings.DEFAULT_COMMAND_TOPIC,
                NorthboundSettings.DEFAULT_RESPONSE_TOPIC,
                NorthboundSettings.DEFAULT_TELEMETRY_TOPIC,
                "nb-memory", "", "",
                false, "", 3000, 2);
    }

    private static OutboundMessage response(String requestId, String deviceId) {
        CommandResponse body = CommandResponse.from(ExecutionResult.success(
                requestId, deviceId, "fn.open", Map.of("ok", true)));
        return new OutboundMessage(
                "msg-" + requestId, Channels.CLOUD, body, MessageHeaders.empty(), MessagePriority.HIGH, 0);
    }
}
