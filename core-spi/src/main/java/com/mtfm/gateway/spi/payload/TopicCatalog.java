package com.mtfm.gateway.spi.payload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备 MQTT 命名 topic 目录。slot → 完整 topic 字符串。
 */
public final class TopicCatalog {

    public static final String DEFAULT_PUB = "default_pub";
    public static final String DEFAULT_SUB = "default_sub";

    private final Map<String, String> slots;

    public TopicCatalog(Map<String, String> slots) {
        this.slots = copySlots(slots);
    }

    public Map<String, String> slots() {
        return Map.copyOf(slots);
    }

    /** 解析 WRITE 发布 topic。 */
    public String resolvePublish(String slot) {
        return resolve(slot, DEFAULT_PUB);
    }

    /** 解析 READ 订阅 topic。 */
    public String resolveSubscribe(String slot) {
        return resolve(slot, DEFAULT_SUB);
    }

    public String resolve(String slot, String fallbackSlot) {
        if (slot != null && !slot.isBlank()) {
            String direct = slots.get(slot.trim());
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
        }
        if (fallbackSlot != null) {
            String fallback = slots.get(fallbackSlot);
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        throw new IllegalArgumentException("无法解析 topic slot: " + slot + "（fallback=" + fallbackSlot + "）");
    }

    /** 所有去重 topic 值（bind 时订阅用）。 */
    public java.util.Set<String> allTopics() {
        return slots.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * 从设备 address 属性解析。
     * <ul>
     *   <li>{@code topics} 对象</li>
     *   <li>兼容 legacy 单字段 {@code topic} → default_pub</li>
     *   <li>兼容 {@code default_pub} / {@code default_sub} 顶层字段</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static TopicCatalog fromAddressMap(Map<String, Object> address) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("address 不能为空");
        }
        Map<String, String> result = new LinkedHashMap<>();
        Object topicsObj = address.get("topics");
        if (topicsObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        putIfPresent(result, DEFAULT_PUB, address.get(DEFAULT_PUB));
        putIfPresent(result, DEFAULT_SUB, address.get(DEFAULT_SUB));
        putIfPresent(result, DEFAULT_PUB, address.get("topic"));
        putIfPresent(result, DEFAULT_PUB, address.get("publishTopic"));
        putIfPresent(result, DEFAULT_SUB, address.get("subscribeTopic"));
        if (!result.containsKey(DEFAULT_PUB)) {
            throw new IllegalArgumentException("address 缺少 default_pub（或 legacy topic）");
        }
        return new TopicCatalog(result);
    }

    /**
     * 合并设备级 topic 覆盖（slot → topic）。
     */
    public TopicCatalog withOverrides(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(slots);
        overrides.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                merged.put(key.trim(), value.trim());
            }
        });
        return new TopicCatalog(merged);
    }

    private static void putIfPresent(Map<String, String> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.putIfAbsent(key, String.valueOf(value));
        }
    }

    private static Map<String, String> copySlots(Map<String, String> raw) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    copy.put(key.trim(), value.trim());
                }
            });
        }
        if (!copy.containsKey(DEFAULT_PUB)) {
            throw new IllegalArgumentException("TopicCatalog 必须包含 default_pub");
        }
        return Map.copyOf(copy);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TopicCatalog other && Objects.equals(slots, other.slots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slots);
    }
}
