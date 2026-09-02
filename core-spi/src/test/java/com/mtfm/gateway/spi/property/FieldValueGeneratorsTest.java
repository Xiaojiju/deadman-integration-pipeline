package com.mtfm.gateway.spi.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldValueGeneratorsTest {

    @Test
    void randomAlnum32Length() {
        Object value = FieldValueGenerators.generate("random_alnum_32");
        assertTrue(value instanceof String);
        assertEquals(32, ((String) value).length());
    }

    @Test
    void timestampMillisIsLong() {
        long before = System.currentTimeMillis();
        Object value = FieldValueGenerators.generate("timestamp_millis");
        long after = System.currentTimeMillis();
        assertTrue(value instanceof Long);
        long ts = (Long) value;
        assertTrue(ts >= before && ts <= after);
    }

    @Test
    void timestampSecondsIsEpochLong() {
        long before = System.currentTimeMillis() / 1000L;
        Object value = FieldValueGenerators.generate("timestamp_seconds");
        long after = System.currentTimeMillis() / 1000L;
        assertTrue(value instanceof Long);
        long ts = (Long) value;
        assertTrue(ts >= before && ts <= after);
        assertTrue(FieldValueGenerators.isPlatformGenerated("epoch_seconds"));
    }

    @Test
    void timestampFollowsDeclaredFieldType() {
        Object secondsAsString = FieldValueGenerators.generate("timestamp_seconds", "string");
        assertInstanceOf(String.class, secondsAsString);
        Long.parseLong((String) secondsAsString);

        Object secondsAsInt = FieldValueGenerators.generate("timestamp_seconds", "int");
        assertInstanceOf(Long.class, secondsAsInt);

        Object millisAsString = FieldValueGenerators.generate("timestamp_millis", "string");
        assertInstanceOf(String.class, millisAsString);
        long millis = Long.parseLong((String) millisAsString);
        assertTrue(millis > 1_000_000_000_000L);
    }

    @Test
    void platformGeneratedDetection() {
        assertTrue(FieldValueGenerators.isPlatformGenerated("random_alnum_32"));
        assertTrue(FieldValueGenerators.isPlatformGenerated("timestamp_millis"));
        assertTrue(FieldValueGenerators.isPlatformGenerated("timestamp_seconds"));
        assertFalse(FieldValueGenerators.isPlatformGenerated(null));
        assertFalse(FieldValueGenerators.isPlatformGenerated(""));
        assertFalse(FieldValueGenerators.isPlatformGenerated("none"));
    }
}
