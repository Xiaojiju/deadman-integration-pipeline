package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从设备 JSON 载荷按 readFields 提取遥测点（入站 READ 用）。
 */
public final class PayloadDisassembler {

    private PayloadDisassembler() {
    }

    public static Map<String, Object> disassemble(Map<String, Object> json, List<WriteFieldOption> readFields) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> source = json;
        if (readFields == null || readFields.isEmpty()) {
            return Map.copyOf(source);
        }
        Map<String, Object> points = new LinkedHashMap<>();
        for (WriteFieldOption field : readFields) {
            if (field == null || field.field() == null || field.field().isBlank()) {
                continue;
            }
            Object value = extractPath(source, field.field());
            if (value != null) {
                points.put(field.field(), value);
            }
        }
        return points.isEmpty() ? Map.copyOf(source) : Map.copyOf(points);
    }

    /** 按点分 path 取值，支持一层 Map 嵌套。 */
    @SuppressWarnings("unchecked")
    public static Object extractPath(Map<String, ?> root, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            return root.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
