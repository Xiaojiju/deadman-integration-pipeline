package com.mtfm.gateway.spi.property;

import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FieldValueGenerator;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 按 {@link FieldValueGenerator} 生成原始值，再按字段 {@link FieldType} 转成协议类型。
 */
public final class FieldValueGenerators {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private FieldValueGenerators() {
    }

    /** 是否平台自动生成字段（非空且可识别）。 */
    public static boolean isPlatformGenerated(String valueGenerator) {
        return FieldValueGenerator.from(valueGenerator) != null;
    }

    /** 生成原始值（时间戳为 Long）；未知 generator 抛异常。 */
    public static Object generate(String valueGenerator) {
        return generate(valueGenerator, null);
    }

    /**
     * 生成并按字段类型转换。string 得到数字字符串，int 得到整数（时间戳用 Long，避免毫秒溢出 Integer）。
     */
    public static Object generate(String valueGenerator, String fieldType) {
        FieldValueGenerator gen = FieldValueGenerator.from(valueGenerator);
        if (gen == null) {
            throw new IllegalArgumentException("未知 valueGenerator: " + valueGenerator);
        }
        Object raw = switch (gen) {
            case RANDOM_ALNUM_32 -> randomAlnum(32);
            case TIMESTAMP_MILLIS -> System.currentTimeMillis();
            case TIMESTAMP_SECONDS -> System.currentTimeMillis() / 1000L;
            case UUID -> UUID.randomUUID().toString();
        };
        return coerce(raw, fieldType);
    }

    /** 将平台生成值转为字段声明类型；未声明类型则保持原始值。 */
    public static Object coerce(Object value, String fieldType) {
        if (value == null || fieldType == null || fieldType.isBlank()) {
            return value;
        }
        FieldType type = FieldType.from(fieldType);
        return switch (type) {
            case STRING, SELECT, PASSWORD -> String.valueOf(value);
            case INT -> toIntegral(value);
            case BOOLEAN -> toBoolean(value);
            case JSON, ARRAY -> value;
        };
    }

    private static Number toIntegral(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return (Number) value;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("无法按 int 转换平台生成值: " + value, ex);
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("无法按 boolean 转换平台生成值: " + value);
    }

    private static String randomAlnum(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }
}
