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
    void literalTopicDoesNotNeedCatalogKey() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of("default_pub", "a/cmd"));
        assertEquals(
                "ydlink/FFFA25101101/thing/action/execute_response",
                catalog.resolveSubscribe("ydlink/FFFA25101101/thing/action/execute_response"));
    }

    @Test
    void dotsBecomeSlashes() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "default_pub", "ydlink.FFFA25101101.thing.action.execute"));
        assertEquals(
                "ydlink/FFFA25101101/thing/action/execute",
                catalog.resolvePublish(null));
        assertEquals(
                "ydlink/FFFA25101101/thing/action/execute",
                catalog.resolvePublish("ydlink.FFFA25101101.thing.action.execute"));
        assertTrue(catalog.allTopics().contains("ydlink/FFFA25101101/thing/action/execute"));
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

    @Test
    void readWithoutPublishSlotStaysSubscribeOnly() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "topics", Map.of("default_pub", "a/cmd", "default_sub", "a/evt")));
        var resolved = TopicRouteResolver.resolve(
                catalog,
                FunctionRoute.empty(),
                Map.of(),
                false);
        org.junit.jupiter.api.Assertions.assertNull(resolved.publishTopic());
        assertEquals("a/evt", resolved.subscribeTopic());
    }

    @Test
    void readWithPublishSlotResolvesBoth() {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(Map.of(
                "topics", Map.of("default_pub", "a/cmd", "default_sub", "a/evt", "poll", "a/poll")));
        var resolved = TopicRouteResolver.resolve(
                catalog,
                new FunctionRoute("poll", "default_sub"),
                Map.of(),
                false);
        assertEquals("a/poll", resolved.publishTopic());
        assertEquals("a/evt", resolved.subscribeTopic());
    }
}
