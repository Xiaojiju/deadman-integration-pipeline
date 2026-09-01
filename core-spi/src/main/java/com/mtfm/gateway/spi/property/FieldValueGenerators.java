package com.mtfm.gateway.spi.property;

import com.mtfm.gateway.spi.model.FieldValueGenerator;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 按 {@link FieldValueGenerator} wire code 生成字段值。
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

    /** 生成字段值；未知 generator 抛异常。 */
    public static Object generate(String valueGenerator) {
        FieldValueGenerator gen = FieldValueGenerator.from(valueGenerator);
        if (gen == null) {
            throw new IllegalArgumentException("未知 valueGenerator: " + valueGenerator);
        }
        return switch (gen) {
            case RANDOM_ALNUM_32 -> randomAlnum(32);
            case TIMESTAMP_MILLIS -> System.currentTimeMillis();
            case UUID -> UUID.randomUUID().toString();
        };
    }

    private static String randomAlnum(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }
}
