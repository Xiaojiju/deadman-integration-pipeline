package com.mtfm.gateway.catalog.payload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.ValueMapping;

import java.util.List;

/** FieldNode / ValueMapping JSON 编解码。 */
public final class PayloadCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
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
            return MAPPER.readValue(json, FieldNode.class);
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
            return MAPPER.readValue(json, MAPPINGS_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("valueMappings 反序列化失败", ex);
        }
    }
}
