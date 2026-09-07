package com.mtfm.gateway.capability.cloud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NorthboundSubscriptionIndexTest {

    @Test
    void exactTopicDoesNotScanWildcards() {
        NorthboundSubscriptionIndex index = new NorthboundSubscriptionIndex();
        List<String> hits = new ArrayList<>();
        index.add("gw/door-1/command", (topic, payload) -> hits.add("exact"));
        index.add("gw/+/command", (topic, payload) -> hits.add("wild"));
        index.dispatch("gw/door-1/command", "{}");
        assertEquals(List.of("exact", "wild"), hits);
        assertFalse(NorthboundSubscriptionIndex.wildcard("gw/door-1/command"));
        assertTrue(NorthboundSubscriptionIndex.wildcard("gw/+/command"));
    }

    @Test
    void unmatchedExactDoesNotFire() {
        NorthboundSubscriptionIndex index = new NorthboundSubscriptionIndex();
        List<String> hits = new ArrayList<>();
        index.add("gw/door-1/command", (topic, payload) -> hits.add(topic));
        index.dispatch("gw/door-2/command", "{}");
        assertEquals(List.of(), hits);
    }
}
