package com.mtfm.gateway.capability.cloud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.TelemetryEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 北向 MQTT / Webhook 共用 JSON。出站必带 {@code requestId} 键（遥测可为空串）。
 */
public final class NorthboundJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private NorthboundJson() {
    }

    public static String stringify(OutboundMessage message) {
        try {
            return MAPPER.writeValueAsString(toMap(message));
        } catch (Exception ex) {
            throw new IllegalStateException("北向出站 JSON 序列化失败", ex);
        }
    }

    public static Map<String, Object> toMap(OutboundMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageId", message.messageId());
        map.put("kind", message.kind().name());
        map.put("requestId", message.requestId() == null ? "" : message.requestId());
        map.put("deviceId", message.deviceId());
        if (message.body() instanceof CommandResponse response) {
            map.put("functionId", response.functionId());
            map.put("status", response.status().name());
            map.put("data", response.data().values());
            if (response.error() != null) {
                map.put("error", errorMap(response.error()));
            }
        } else if (message.body() instanceof TelemetryEvent event) {
            map.put("functionId", event.functionId());
            map.put("points", event.points().values());
        }
        return map;
    }

    public static NorthboundCommand parseCommand(String json, String fallbackDeviceId) {
        Map<String, Object> raw = parseObject(json);
        String requestId = text(raw.get("requestId"));
        String deviceId = text(raw.get("deviceId"));
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = fallbackDeviceId;
        }
        String functionId = text(raw.get("functionId"));
        Map<String, Object> arguments = argumentsOf(raw.get("arguments"));
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("北向命令缺少 deviceId");
        }
        if (functionId == null || functionId.isBlank()) {
            throw new IllegalArgumentException("北向命令缺少 functionId");
        }
        return new NorthboundCommand(requestId, deviceId, functionId, arguments);
    }

    static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = MAPPER.readValue(json, MAP);
            return value == null ? Map.of() : value;
        } catch (Exception ex) {
            throw new IllegalArgumentException("北向命令 JSON 无法解析", ex);
        }
    }

    private static Map<String, Object> errorMap(Failure error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", error.code());
        map.put("message", error.message());
        map.put("source", error.source());
        map.put("retryable", error.retryable());
        return map;
    }

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argumentsOf(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    copy.put(String.valueOf(key), value);
                }
            });
            return copy;
        }
        return Map.of();
    }
}
