package com.mtfm.gateway.capability.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorthboundTopicsTest {

    @Test
    void plusMatchesSingleLevel() {
        assertTrue(NorthboundTopics.matches("gw/+/command", "gw/door-1/command"));
        assertFalse(NorthboundTopics.matches("gw/+/command", "gw/door-1/extra/command"));
        assertFalse(NorthboundTopics.matches("gw/+/command", "gw/door-1/response"));
    }

    @Test
    void hashMatchesRest() {
        assertTrue(NorthboundTopics.matches("gw/#", "gw/door-1/command"));
        assertTrue(NorthboundTopics.matches("gw/#", "gw/a/b/c"));
        assertFalse(NorthboundTopics.matches("other/#", "gw/a"));
    }

    @Test
    void deviceIdFromFirstPlus() {
        assertEquals("door-1", NorthboundTopics.deviceIdFromTopic("gw/+/command", "gw/door-1/command"));
        assertNull(NorthboundTopics.deviceIdFromTopic("fixed/command", "fixed/command"));
    }

    @Test
    void expandDevicePlaceholder() {
        assertEquals("gw/door-1/response",
                NorthboundTopics.expand("gw/{deviceId}/response", "door-1"));
    }
}
