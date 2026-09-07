package com.mtfm.gateway.capability.cloud;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 北向订阅索引：精确 topic 直接命中，通配过滤器才线性扫。
 *
 * <p>使用示例：
 * <pre>{@code
 * index.add("ydlink/dev/response", this::onExact);
 * index.add("ydlink/+/status", this::onWildcard);
 * index.dispatch("ydlink/dev/response", payload);
 * }</pre>
 */
final class NorthboundSubscriptionIndex {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BiConsumer<String, String>>> exact =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Wildcard> wildcards = new CopyOnWriteArrayList<>();

    void add(String topicFilter, BiConsumer<String, String> handler) {
        if (topicFilter == null || handler == null) {
            return;
        }
        if (wildcard(topicFilter)) {
            wildcards.add(new Wildcard(topicFilter, handler));
            return;
        }
        exact.computeIfAbsent(topicFilter, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    void dispatch(String topic, String payload) {
        CopyOnWriteArrayList<BiConsumer<String, String>> handlers = exact.get(topic);
        if (handlers != null) {
            for (BiConsumer<String, String> handler : handlers) {
                handler.accept(topic, payload);
            }
        }
        for (Wildcard wildcard : wildcards) {
            if (NorthboundTopics.matches(wildcard.filter(), topic)) {
                wildcard.handler().accept(topic, payload);
            }
        }
    }

    void clear() {
        exact.clear();
        wildcards.clear();
    }

    int size() {
        int n = 0;
        for (CopyOnWriteArrayList<BiConsumer<String, String>> handlers : exact.values()) {
            n += handlers.size();
        }
        return n + wildcards.size();
    }

    static boolean wildcard(String filter) {
        return filter.indexOf('+') >= 0 || filter.indexOf('#') >= 0;
    }

    private record Wildcard(String filter, BiConsumer<String, String> handler) {
    }
}
