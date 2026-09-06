package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从设备 JSON 载荷按 readFields 提取点位（入站 READ / 应答投影用）。
 */
public final class PayloadDisassembler {

    private PayloadDisassembler() {
    }

    public static Map<String, Object> disassemble(Map<String, Object> json, List<WriteFieldOption> readFields) {
        return project(json, readFields, List.of());
    }

    /**
     * 按 readFields 拾取：没有的字段跳过；输出键优先 callerField。
     * 字段 options（否则 fallbackOptions）把设备侧 optionValue 翻成北向 mappingValue。
     * 配了字段但一个都拾不到时返回空 Map，不再回退整段 JSON。
     */
    public static Map<String, Object> project(
            Map<String, Object> json,
            List<WriteFieldOption> readFields,
            List<ValueOption> fallbackOptions) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        if (readFields == null || readFields.isEmpty()) {
            return Map.copyOf(json);
        }
        Map<String, Object> points = new LinkedHashMap<>();
        for (WriteFieldOption field : readFields) {
            if (field == null || field.field() == null || field.field().isBlank()) {
                continue;
            }
            Object value = extractPath(json, field.field());
            if (value == null) {
                continue;
            }
            String key = field.callerField() != null && !field.callerField().isBlank()
                    ? field.callerField()
                    : field.field();
            List<ValueOption> options = field.options() != null && !field.options().isEmpty()
                    ? field.options()
                    : fallbackOptions;
            points.put(key, mapInboundValue(value, options));
        }
        return Map.copyOf(points);
    }

    /**
     * 设备/协议值 → 北向业务值。匹配 optionValue 或已是 mappingValue 时输出 mappingValue。
     */
    public static Object mapInboundValue(Object raw, List<ValueOption> options) {
        if (raw == null || options == null || options.isEmpty()) {
            return raw;
        }
        String text = String.valueOf(raw);
        for (ValueOption option : options) {
            if (option == null) {
                continue;
            }
            if (text.equals(option.optionValue()) || text.equals(option.mappingValue())) {
                return option.mappingValue();
            }
        }
        return raw;
    }

    /**
     * 按点分 path 取值，数字段视为数组下标（如 {@code params.0}）。
     * 嵌套取不到时回退整键（扁平 Map）。
     */
    @SuppressWarnings("unchecked")
    public static Object extractPath(Map<String, ?> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Object nested = FieldTreePaths.getNested(map, path);
        if (nested != null) {
            return nested;
        }
        return map.get(path);
    }
}
