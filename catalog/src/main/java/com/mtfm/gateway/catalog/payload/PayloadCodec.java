package com.mtfm.gateway.catalog.payload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.ValueMapping;

import java.util.List;

/** FieldNode / ValueMapping JSON 编解码。 */
public final class PayloadCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<List<ValueMapping>> MAPPINGS_TYPE = new TypeReference<>() {
    };

    private PayloadCodec() {
    }

    public static String writeFieldNode(FieldNode node) {
        if (node == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalArgumentException("structSchema 序列化失败", ex);
        }
    }

    public static FieldNode readFieldNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json.trim(), FieldNode.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("structSchema 反序列化失败", ex);
        }
    }

    public static String writeMappings(List<ValueMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(mappings);
        } catch (Exception ex) {
            throw new IllegalArgumentException("valueMappings 序列化失败", ex);
        }
    }

    public static List<ValueMapping> readMappings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ValueMapping> mappings = MAPPER.readValue(json.trim(), MAPPINGS_TYPE);
            return mappings == null ? List.of() : mappings;
        } catch (Exception ex) {
            throw new IllegalArgumentException("valueMappings 反序列化失败", ex);
        }
    }
}
