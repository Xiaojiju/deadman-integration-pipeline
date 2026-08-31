package com.mtfm.gateway.spi.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * 交给 {@link com.mtfm.gateway.spi.capability.Driver#decode} 的原始入站。禁止包含客户端或 Channel 句柄。
 *
 * <p>使用示例：
 * <pre>{@code
 * RawInbound raw = RawInbound.builder()
 *         .capabilityType("modbus-tcp")
 *         .deviceIdHint("dev-001")
 *         .bytes(frameBytes)
 *         .headers(Map.of("source", "tcp:502"))
 *         .build();
 * pipeline.acceptRaw(raw);
 * }</pre>
 *
 * @param capabilityType 南向能力类型提示
 * @param deviceIdHint   设备 ID 提示，可空
 * @param bytes          原始字节
 * @param text           文本载荷，可空
 * @param headers        传输头
 */
public record RawInbound(
        String capabilityType,
        String deviceIdHint,
        byte[] bytes,
        String text,
        Map<String, String> headers
) {

    public RawInbound {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        headers = Maps.copyStringMap(headers);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /** 返回 UTF-8 文本；优先 {@code text}，否则从 {@code bytes} 解码。 */
    public String textOrUtf8() {
        if (text != null) {
            return text;
        }
        if (bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 创建原始入站构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String capabilityType;
        private String deviceIdHint;
        private byte[] bytes;
        private String text;
        private Map<String, String> headers = Map.of();

        private Builder() {
        }

        public Builder capabilityType(String capabilityType) {
            this.capabilityType = capabilityType;
            return this;
        }

        public Builder deviceIdHint(String deviceIdHint) {
            this.deviceIdHint = deviceIdHint;
            return this;
        }

        public Builder bytes(byte[] bytes) {
            this.bytes = bytes;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public RawInbound build() {
            return new RawInbound(capabilityType, deviceIdHint, bytes, text, headers);
        }
    }
}
