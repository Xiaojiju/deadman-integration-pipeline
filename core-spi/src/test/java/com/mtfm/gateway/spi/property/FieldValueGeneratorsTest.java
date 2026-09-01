package com.mtfm.gateway.spi.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void platformGeneratedDetection() {
        assertTrue(FieldValueGenerators.isPlatformGenerated("random_alnum_32"));
        assertTrue(FieldValueGenerators.isPlatformGenerated("timestamp_millis"));
        assertFalse(FieldValueGenerators.isPlatformGenerated(null));
        assertFalse(FieldValueGenerators.isPlatformGenerated(""));
        assertFalse(FieldValueGenerators.isPlatformGenerated("none"));
    }
}
