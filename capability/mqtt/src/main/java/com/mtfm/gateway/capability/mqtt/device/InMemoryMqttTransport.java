package com.mtfm.gateway.capability.mqtt.device;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 内存 MQTT 传输：按 channelId 引用计数；subscribe 按 topic 分发。
 */
public final class InMemoryMqttTransport implements MqttTransport {

    private final ConcurrentHashMap<String, AtomicInteger> refs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BiConsumer<String, String>>> subscribers =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> published = new CopyOnWriteArrayList<>();

    @Override
    public void retain(String channelId, MqttBrokerConnection connection) {
        refs.computeIfAbsent(channelId, key -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void release(String channelId) {
        AtomicInteger count = refs.get(channelId);
        if (count == null) {
            return;
        }
        if (count.decrementAndGet() <= 0) {
            refs.remove(channelId);
            subscribers.keySet().removeIf(key -> key.startsWith(channelId + "\0"));
        }
    }

    @Override
    public int refCount(String channelId) {
        AtomicInteger count = refs.get(channelId);
        return count == null ? 0 : count.get();
    }

    @Override
    public void publish(String channelId, String topic, String payload) {
        published.add(channelId + "|" + topic + "|" + payload);
        CopyOnWriteArrayList<BiConsumer<String, String>> handlers = subscribers.get(key(channelId, topic));
        if (handlers != null) {
            handlers.forEach(handler -> handler.accept(topic, payload));
        }
    }

    @Override
    public void subscribe(String channelId, String topic, BiConsumer<String, String> handler) {
        subscribers.computeIfAbsent(key(channelId, topic), ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public List<String> snapshot() {
        return new ArrayList<>(published);
    }

    private static String key(String channelId, String topic) {
        return channelId + "\0" + topic;
    }
}
