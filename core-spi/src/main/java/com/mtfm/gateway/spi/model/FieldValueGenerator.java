package com.mtfm.gateway.spi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 平台侧字段值生成器。配置在 {@link com.mtfm.gateway.spi.property.WriteFieldOption#valueGenerator()}，
 * 执行指令时由网关自动生成，调用方无需传递。
 */
public enum FieldValueGenerator {

    /** 32 位随机字母数字串（如 seq）。 */
    RANDOM_ALNUM_32("random_alnum_32"),
    /** 当前时间戳，毫秒（如 at）；落库类型由字段 type 决定。 */
    TIMESTAMP_MILLIS("timestamp_millis"),
    /** 当前时间戳，秒；落库类型由字段 type 决定（string → {@code "1756..."}，int → 数字）。 */
    TIMESTAMP_SECONDS("timestamp_seconds"),
    /** 标准 UUID 字符串。 */
    UUID("uuid");

    private final String code;

    FieldValueGenerator(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** 解析 wire；空或 none 表示无平台生成。 */
    @JsonCreator
    public static FieldValueGenerator from(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "random_alnum_32", "random32", "seq" -> RANDOM_ALNUM_32;
            case "timestamp_millis", "timestamp", "at" -> TIMESTAMP_MILLIS;
            case "timestamp_seconds", "timestamp_sec", "epoch_seconds" -> TIMESTAMP_SECONDS;
            case "uuid" -> UUID;
            default -> null;
        };
    }
}
