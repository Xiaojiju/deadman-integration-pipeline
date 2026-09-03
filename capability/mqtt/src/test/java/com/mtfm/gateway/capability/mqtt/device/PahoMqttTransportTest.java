package com.mtfm.gateway.capability.mqtt.device;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PahoMqttTransportTest {

    @Test
    void unsubscribeRemovesHandlerWithoutBroker() {
        try (PahoMqttTransport transport = new PahoMqttTransport()) {
            AtomicInteger hits = new AtomicInteger();
            BiConsumer<String, String> first = (topic, payload) -> hits.incrementAndGet();
            BiConsumer<String, String> second = (topic, payload) -> hits.incrementAndGet();
            transport.subscribe("ch-1", "dev/state", first);
            transport.subscribe("ch-1", "dev/state", second);
            assertEquals(2, transport.handlerCount("ch-1", "dev/state"));

            transport.unsubscribe("ch-1", "dev/state", first);
            assertEquals(1, transport.handlerCount("ch-1", "dev/state"));

            transport.unsubscribe("ch-1", "dev/state", second);
            assertEquals(0, transport.handlerCount("ch-1", "dev/state"));
        }
    }
}
