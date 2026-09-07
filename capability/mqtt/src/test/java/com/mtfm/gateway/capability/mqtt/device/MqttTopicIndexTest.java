package com.mtfm.gateway.capability.mqtt.device;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTopicIndexTest {

    @Test
    void lookupReturnsRegisteredTargets() {
        MqttTopicIndex index = new MqttTopicIndex();
        index.register("ch-1", "dev/1/status", "dev-1", "fn.status", false);
        index.register("ch-1", "dev/1/status", "dev-1", "fn.reply", true);
        List<MqttTopicIndex.TopicTarget> hits = index.lookup("ch-1", "dev/1/status");
        assertEquals(2, hits.size());
        assertEquals("fn.status", hits.get(0).functionId());
        assertTrue(hits.get(1).reply());
    }

    @Test
    void unregisterDeviceDropsOnlyThatDevice() {
        MqttTopicIndex index = new MqttTopicIndex();
        index.register("ch-1", "shared", "dev-1", "fn.a", false);
        index.register("ch-1", "shared", "dev-2", "fn.b", false);
        index.unregisterDevice("ch-1", "dev-1");
        List<MqttTopicIndex.TopicTarget> hits = index.lookup("ch-1", "shared");
        assertEquals(1, hits.size());
        assertEquals("dev-2", hits.get(0).deviceId());
    }

    @Test
    void otherChannelDoesNotMatch() {
        MqttTopicIndex index = new MqttTopicIndex();
        index.register("ch-1", "topic", "dev-1", "fn.a", false);
        assertEquals(List.of(), index.lookup("ch-2", "topic"));
    }
}
