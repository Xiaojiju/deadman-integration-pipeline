package com.mtfm.gateway.capability.mqtt.device;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttBrokerConnectionTest {

    @Test
    void missingHostIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MqttBrokerConnection.fromMap(Map.of("port", 1883)));
        assertEquals("MQTT 通道缺少 host", ex.getMessage());
    }

    @Test
    void emptyConnectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MqttBrokerConnection.fromMap(Map.of()));
    }

    @Test
    void brokerUriSuppliesHost() {
        MqttBrokerConnection conn = MqttBrokerConnection.fromMap(Map.of("broker", "mqtt://example.com:1884"));
        assertEquals("example.com", conn.host());
        assertEquals(1884, conn.port());
        assertEquals("tcp://example.com:1884", conn.serverUri());
    }
}
