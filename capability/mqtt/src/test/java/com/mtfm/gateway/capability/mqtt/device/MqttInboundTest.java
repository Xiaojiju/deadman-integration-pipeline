package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.RawInbound;
import com.mtfm.gateway.spi.port.MqttSubscribeRoute;
import com.mtfm.gateway.spi.port.MqttSubscribeRouteCatalog;
import com.mtfm.gateway.spi.port.PipelineIngress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttInboundTest {

    @Test
    void subscribeMessageRoutesToIngress() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        RecordingIngress ingress = new RecordingIngress();
        MqttSubscribeRouteCatalog routes = (deviceCode, address) -> List.of(
                new MqttSubscribeRoute(String.valueOf(address.get("default_sub")), "readState"));
        MqttExecutor executor = new MqttExecutor(transport);
        executor.attach(ingress, routes);
        executor.bind(binding("door-1", "ch-1", Map.of(
                "default_pub", "dev/door/cmd",
                "default_sub", "dev/door/state")));

        transport.publish("ch-1", "dev/door/state", "{\"temp\":25}");

        assertEquals(1, ingress.raws.size());
        RawInbound raw = ingress.raws.get(0);
        assertEquals("door-1", raw.deviceIdHint());
        assertEquals("readState", raw.headers().get("functionId"));
        assertEquals("dev/door/state", raw.headers().get("topic"));
    }

    @Test
    void executeWithoutPublishHintIsSubscribeOnly() {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        MqttExecutor executor = new MqttExecutor(transport);
        executor.bind(binding("A", "ch-1", Map.of("default_pub", "dev/A/cmd")));
        var result = executor.execute(FunctionCommand.of(
                "A", "readState", Map.of(), Map.of("mqtt.subscribeOnly", "true")));
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals("subscribe-only", result.data().get("mode").map(String::valueOf).orElse(""));
    }

    private static DeviceEndpointBinding binding(String deviceId, String channelId, Map<String, Object> address) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                MqttCapability.TYPE,
                Attributes.from(Map.of("broker", "mqtt://localhost")),
                Attributes.from(address));
    }

    private static final class RecordingIngress implements PipelineIngress {
        private final List<RawInbound> raws = new ArrayList<>();

        @Override
        public boolean acceptRaw(RawInbound raw) {
            raws.add(raw);
            return true;
        }

        @Override
        public boolean accept(com.mtfm.gateway.spi.model.EnvelopeDraft draft) {
            return false;
        }
    }
}
