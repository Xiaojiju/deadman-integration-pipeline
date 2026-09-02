package com.mtfm.gateway.spi.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** VALUE 映射目标：整包标量或按 path patch 字段树。 */
public enum MappingTarget {

    FILL_ROOT,
    PATCH_FIELDS;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MappingTarget from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PATCH_FIELDS;
        }
        String key = raw.trim().toUpperCase();
        return MappingTarget.valueOf(key);
    }

    @JsonValue
    public String wire() {
        return name();
    }
}
