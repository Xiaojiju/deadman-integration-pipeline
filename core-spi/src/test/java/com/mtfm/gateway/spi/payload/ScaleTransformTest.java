package com.mtfm.gateway.spi.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaleTransformTest {

    @Test
    void divideTenRoundTripsTemperature() {
        Object inbound = ScaleTransform.inbound(238, "divide", "10");
        assertEquals(23.8, ((Number) inbound).doubleValue(), 0.0001);
        Object outbound = ScaleTransform.outbound(23.8, "divide", "10");
        assertEquals(238L, outbound);
    }

    @Test
    void addAndSubtractInverse() {
        assertEquals(15L, ScaleTransform.inbound(10, "add", "5"));
        assertEquals(10L, ScaleTransform.outbound(15, "add", "5"));
    }

    @Test
    void unconfiguredLeavesRaw() {
        assertEquals(238, ScaleTransform.inbound(238, "none", null));
        assertTrue(!ScaleTransform.configured(null, "10"));
        assertTrue(!ScaleTransform.configured("none", "10"));
        assertTrue(!ScaleTransform.configured("", "10"));
    }
}
