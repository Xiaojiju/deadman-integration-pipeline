package com.mtfm.gateway.spi.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 字段值来源：决定 CommandAssembler 如何填树。
 */
public enum FieldSource {

    CALLER,
    PLATFORM,
    DEVICE,
    CONSTANT,
    MAPPED;

    @JsonCreator
    public static FieldSource from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CALLER;
        }
        return FieldSource.valueOf(raw.trim().toUpperCase());
    }

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }
}
