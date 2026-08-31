package com.mtfm.gateway.spi.model;

/**
 * Map 防御拷贝工具，仅供 {@code com.mtfm.gateway.spi.model} 包内模型使用。
 */
final class Maps {

    private Maps() {
    }

    static java.util.Map<String, Object> copyObjectMap(java.util.Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(java.util.Objects.requireNonNull(key, "map key"), value));
        return java.util.Collections.unmodifiableMap(copy);
    }

    static java.util.Map<String, String> copyStringMap(java.util.Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(java.util.Objects.requireNonNull(key, "map key"), value));
        return java.util.Collections.unmodifiableMap(copy);
    }
}
