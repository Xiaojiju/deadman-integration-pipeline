package com.mtfm.gateway.spi.property;

/**
 * 写访问模式：标量 VALUE 或多字段 STRUCT（对齐旧 property ValueAccessType）。
 */
public enum ValueAccessType {
    VALUE,
    STRUCT;

    public static ValueAccessType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return VALUE;
        }
        return ValueAccessType.valueOf(raw.trim().toUpperCase());
    }
}
