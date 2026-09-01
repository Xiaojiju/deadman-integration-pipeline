package com.mtfm.gateway.spi.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 载荷模式：VALUE 简化调用 / STRUCT 按字段填表。 */
public enum PayloadMode {

    VALUE,
    STRUCT;

    @JsonCreator
    public static PayloadMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRUCT;
        }
        return PayloadMode.valueOf(raw.trim().toUpperCase());
    }

    @JsonValue
    public String wire() {
        return name();
    }
}
