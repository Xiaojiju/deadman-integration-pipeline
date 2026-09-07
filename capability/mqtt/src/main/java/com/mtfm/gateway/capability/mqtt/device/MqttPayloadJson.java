package com.mtfm.gateway.capability.mqtt.device;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

final class MqttPayloadJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MqttPayloadJson() {
    }

    static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return Map.of("_value", trimmed);
        }
        try {
            return MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of("_raw", text);
        }
    }

    static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
