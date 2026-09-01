package com.mtfm.gateway.spi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Schema / 表单字段固定类型（wire 为小写 code）。
 */
public enum FieldType {

    STRING("string"),
    INT("int"),
    BOOLEAN("boolean"),
    SELECT("select"),
    PASSWORD("password"),
    JSON("json");

    private final String code;

    FieldType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /**
     * 解析 wire / 别名；未知回落 {@link #STRING}。
     * <p>兼容 {@code integer} → {@link #INT}。
     */
    @JsonCreator
    public static FieldType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRING;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "string", "text" -> STRING;
            case "int", "integer", "number", "long" -> INT;
            case "boolean", "bool" -> BOOLEAN;
            case "select", "enum", "choice" -> SELECT;
            case "password", "secret" -> PASSWORD;
            case "json", "object" -> JSON;
            default -> STRING;
        };
    }
}
