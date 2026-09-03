package com.mtfm.gateway.capability.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NorthboundJsonTest {

    @Test
    void parseCommandPrefersPayloadDeviceId() {
        NorthboundCommand command = NorthboundJson.parseCommand(
                "{\"requestId\":\"r1\",\"deviceId\":\"from-payload\",\"functionId\":\"fn.open\",\"arguments\":{\"a\":1}}",
                "from-topic");
        assertEquals("from-payload", command.deviceId());
        assertEquals("r1", command.requestId());
        assertEquals(1, command.arguments().get("a"));
    }

    @Test
    void parseCommandFallsBackToTopicDeviceId() {
        NorthboundCommand command = NorthboundJson.parseCommand(
                "{\"functionId\":\"fn.open\"}",
                "door-1");
        assertEquals("door-1", command.deviceId());
    }

    @Test
    void parseCommandRejectsMissingFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> NorthboundJson.parseCommand("{\"deviceId\":\"door-1\"}", null));
    }
}
