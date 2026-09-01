package com.mtfm.gateway.spi.payload;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicCatalogTest {

    @Test
    void parseLegacyTopicAsDefaultPub() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of("topic", "a/b/cmd"));
        assertEquals("a/b/cmd", catalog.resolvePublish(null));
    }

    @Test
    void resolveSlotWithOverride() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "topics", Map.of(
                        "default_pub", "a/cmd",
                        "door_cmd", "a/door/write")));
        TopicCatalog overridden = catalog.withOverrides(Map.of("door_cmd", "custom/door"));
        assertEquals("custom/door", overridden.resolvePublish("door_cmd"));
        assertEquals("a/cmd", overridden.resolvePublish(null));
    }

    @Test
    void allTopicsForSubscribe() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "topics", Map.of(
                        "default_pub", "a/cmd",
                        "default_sub", "a/evt",
                        "door_evt", "a/door/read")));
        Set<String> all = catalog.allTopics();
        assertEquals(3, all.size());
        assertTrue(all.contains("a/door/read"));
    }

    @Test
    void routeResolverWrite() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "topics", Map.of("default_pub", "a/cmd", "door_cmd", "a/door/write")));
        var resolved = TopicRouteResolver.resolve(
                catalog,
                new FunctionRoute("door_cmd", null),
                Map.of(),
                true);
        assertEquals("a/door/write", resolved.publishTopic());
    }
}
