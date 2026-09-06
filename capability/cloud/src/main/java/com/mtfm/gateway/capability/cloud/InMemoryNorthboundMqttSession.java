package com.mtfm.gateway.capability.cloud;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 进程内北向 MQTT：支持 {@code +}/{@code #} 通配，与南向 {@code InMemoryMqttTransport} 隔离。
 */
public final class InMemoryNorthboundMqttSession implements NorthboundMqttSession {

    private final CopyOnWriteArrayList<String> published = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @Override
    public void start() {
    }

    @Override
    public void publish(String topic, String payload) {
        published.add(topic + "|" + (payload == null ? "" : payload));
        for (Subscription subscription : subscriptions) {
            if (NorthboundTopics.matches(subscription.filter(), topic)) {
                subscription.handler().accept(topic, payload == null ? "" : payload);
            }
        }
    }

    @Override
    public void subscribe(String topicFilter, BiConsumer<String, String> handler) {
        subscriptions.add(new Subscription(topicFilter, handler));
    }

    public List<String> snapshot() {
        return List.copyOf(published);
    }

    public int subscriptionCount() {
        return subscriptions.size();
    }

    @Override
    public void close() {
        subscriptions.clear();
    }

    private record Subscription(String filter, BiConsumer<String, String> handler) {
    }
}
