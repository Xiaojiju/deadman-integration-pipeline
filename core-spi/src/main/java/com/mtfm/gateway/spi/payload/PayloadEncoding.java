package com.mtfm.gateway.spi.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 南向载荷编码：JSON 对象 / 定长 hex 文本 / 原始字节。 */
public enum PayloadEncoding {

    JSON,
    HEX,
    BINARY;

    @JsonCreator
    public static PayloadEncoding from(String raw) {
        if (raw == null || raw.isBlank()) {
            return JSON;
        }
        return PayloadEncoding.valueOf(raw.trim().toUpperCase());
    }

    @JsonValue
    public String wire() {
        return name();
    }

    public boolean isFramed() {
        return this == HEX || this == BINARY;
    }
}
