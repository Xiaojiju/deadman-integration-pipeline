package com.mtfm.gateway.catalog.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.option.Option;
import com.mtfm.gateway.spi.option.OptionTrees;

import java.util.Map;

/**
 * catalog 模块专用 JSON 编解码工具，不进入 core-runtime。
 *
 * <p>负责 Map / Option / Attributes 与数据库 TEXT 列之间的转换：
 * <ul>
 *   <li>{@link #write(Object)} — 表单值 Map → JSON 字符串落库</li>
 *   <li>{@link #readMap(String)} — JSON → Map，供校验与投影</li>
 *   <li>{@link #option(String)} — JSON → {@link Option} 树，兼容 legacy 格式</li>
 *   <li>{@link #attributes(String)} — JSON → {@link Attributes}，供 DeviceEndpointBinding 投影</li>
 * </ul>
 */
public final class JsonMaps {

    /** 兼容列占位。运行时只认 EAV，JSON 列不再双写业务值。 */
    public static final String EMPTY_OBJECT = "{}";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonMaps() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 反序列化失败", ex);
        }
    }

    public static Attributes attributes(String json) {
        return Attributes.from(readMap(json));
    }

    public static Option option(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object raw = MAPPER.readValue(json, Object.class);
            return OptionTrees.fromUnknown(raw);
        } catch (JsonProcessingException ex) {
            return Option.fromLegacy(json);
        }
    }
}
