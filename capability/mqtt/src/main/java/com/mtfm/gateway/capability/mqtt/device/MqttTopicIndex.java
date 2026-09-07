package com.mtfm.gateway.capability.mqtt.device;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 南向 MQTT 订阅索引：channel + topic → 设备功能目标。
 *
 * <p>使用示例：
 * <pre>{@code
 * index.register(channelId, "dev/1/status", "dev-1", "fn.status", false);
 * List<MqttTopicIndex.TopicTarget> hits = index.lookup(channelId, "dev/1/status");
 * index.unregisterDevice(channelId, "dev-1");
 * }</pre>
 */
final class MqttTopicIndex {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TopicTarget>> topicIndex = new ConcurrentHashMap<>();

    void register(String channelId, String topic, String deviceId, String functionId, boolean reply) {
        topicIndex.computeIfAbsent(indexKey(channelId, topic), key -> new CopyOnWriteArrayList<>())
                .add(new TopicTarget(deviceId, functionId, reply));
    }

    void unregisterDevice(String channelId, String deviceId) {
        for (Map.Entry<String, CopyOnWriteArrayList<TopicTarget>> entry : topicIndex.entrySet()) {
            if (!entry.getKey().startsWith(channelId + "\0")) {
                continue;
            }
            entry.getValue().removeIf(target -> deviceId.equals(target.deviceId()));
            if (entry.getValue().isEmpty()) {
                topicIndex.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    List<TopicTarget> lookup(String channelId, String topic) {
        CopyOnWriteArrayList<TopicTarget> direct = topicIndex.get(indexKey(channelId, topic));
        return direct == null ? List.of() : List.copyOf(direct);
    }

    private static String indexKey(String channelId, String topic) {
        return channelId + "\0" + topic;
    }

    record TopicTarget(String deviceId, String functionId, boolean reply) {
    }
}
