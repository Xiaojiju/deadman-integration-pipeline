package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.dto.NorthboundView;
import com.mtfm.gateway.catalog.dto.NorthboundWriteRequest;
import com.mtfm.gateway.catalog.entity.NorthboundEntity;
import com.mtfm.gateway.spi.northbound.NorthboundLiveStatus;
import com.mtfm.gateway.spi.northbound.NorthboundSettings;
import com.mtfm.gateway.spi.secret.SecretCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogNorthboundTest {

    @Test
    void maskKeepsSealedPassword() {
        NorthboundEntity existing = new NorthboundEntity();
        existing.setMqttPassword("sealed-old");
        NorthboundEntity next = CatalogNorthbound.merge(existing, write(
                true, "paho", "tcp://broker:1883", CatalogNorthbound.SECRET_MASK,
                false, "", 3000, 2), SecretCodec.identity());
        assertEquals("sealed-old", next.getMqttPassword());
    }

    @Test
    void blankPasswordKeepsExisting() {
        NorthboundEntity existing = new NorthboundEntity();
        existing.setMqttPassword("sealed-old");
        NorthboundEntity next = CatalogNorthbound.merge(existing, write(
                true, "paho", "tcp://broker:1883", "",
                false, "", 3000, 2), SecretCodec.identity());
        assertEquals("sealed-old", next.getMqttPassword());
    }

    @Test
    void newPasswordIsSealedAndOpenedForRuntime() {
        SecretCodec codec = new SecretCodec() {
            @Override
            public String seal(String plaintext) {
                return "SEAL:" + plaintext;
            }

            @Override
            public String open(String stored) {
                return stored != null && stored.startsWith("SEAL:") ? stored.substring(5) : stored;
            }
        };
        NorthboundEntity next = CatalogNorthbound.merge(null, write(
                true, "paho", "tcp://broker:1883", "secret",
                false, "", 3000, 2), codec);
        assertEquals("SEAL:secret", next.getMqttPassword());
        NorthboundSettings settings = CatalogNorthbound.toSettings(next, codec);
        assertEquals("secret", settings.mqttPassword());
        NorthboundView view = CatalogNorthbound.toView(next, NorthboundLiveStatus.idle());
        assertEquals(CatalogNorthbound.SECRET_MASK, view.mqttPassword());
        assertTrue(view.mqttPasswordSet());
        assertFalse(view.mqttLive());
    }

    @Test
    void emptyEntityViewUsesDefaults() {
        NorthboundView view = CatalogNorthbound.toView(null, new NorthboundLiveStatus(false, "down", true));
        assertFalse(view.mqttEnabled());
        assertEquals(NorthboundSettings.TRANSPORT_PAHO, view.mqttTransport());
        assertEquals(NorthboundSettings.DEFAULT_COMMAND_TOPIC, view.mqttCommandTopic());
        assertEquals("", view.mqttPassword());
        assertFalse(view.mqttPasswordSet());
        assertEquals("down", view.mqttError());
        assertTrue(view.httpLive());
    }

    @Test
    void rejectsUnknownTransport() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CatalogNorthbound.merge(null, write(
                        true, "kafka", "", "",
                        false, "", 3000, 2), SecretCodec.identity()));
        assertTrue(ex.getMessage().contains("paho"));
    }

    @Test
    void rejectsNonPositiveWebhookLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogNorthbound.merge(null, write(
                        false, "paho", "", "",
                        true, "http://hook", 0, 2), SecretCodec.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogNorthbound.merge(null, write(
                        false, "paho", "", "",
                        true, "http://hook", 3000, 0), SecretCodec.identity()));
    }

    private static NorthboundWriteRequest write(
            boolean mqttEnabled,
            String transport,
            String url,
            String password,
            boolean httpEnabled,
            String webhookUrl,
            int timeoutMs,
            int maxAttempts) {
        return new NorthboundWriteRequest(
                mqttEnabled, transport, url,
                NorthboundSettings.DEFAULT_COMMAND_TOPIC,
                NorthboundSettings.DEFAULT_RESPONSE_TOPIC,
                NorthboundSettings.DEFAULT_TELEMETRY_TOPIC,
                NorthboundSettings.DEFAULT_CLIENT_ID,
                "", password,
                httpEnabled, webhookUrl, timeoutMs, maxAttempts);
    }
}
