package com.mtfm.gateway.capability.hikvision.isapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 海康 ISAPI JSON 辅助，对齐旧网关 {@code JSONUtils} 的常用方法。
 */
public final class HikvisionJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private HikvisionJson() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }

    public static int getAsInt(String json, String key) {
        JsonNode node = readTree(json).get(key);
        if (node == null || node.isNull()) {
            return 0;
        }
        return node.isInt() ? node.intValue() : Integer.parseInt(node.asText());
    }

    public static String getAsString(String json, String key) {
        JsonNode node = readTree(json).get(key);
        return node == null || node.isNull() ? null : node.asText();
    }

    public static String wrapJson(String json, String wrapField) {
        try {
            JsonNode inner = (json == null || json.isBlank()) ? MAPPER.createObjectNode() : MAPPER.readTree(json);
            ObjectNode wrapper = MAPPER.createObjectNode();
            wrapper.set(wrapField, inner);
            return MAPPER.writeValueAsString(wrapper);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 包装失败", ex);
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 解析失败", ex);
        }
    }
}
