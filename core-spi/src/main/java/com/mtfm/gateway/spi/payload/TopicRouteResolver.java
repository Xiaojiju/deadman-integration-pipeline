package com.mtfm.gateway.spi.payload;

import java.util.Map;

/** 解析功能级 topic slot + 设备 catalog → 实际 MQTT topic。 */
public final class TopicRouteResolver {

    public static final String MQTT_PUBLISH_TOPIC_HINT = "mqtt.publishTopic";
    public static final String MQTT_SUBSCRIBE_TOPIC_HINT = "mqtt.subscribeTopic";

    private TopicRouteResolver() {
    }

    public record ResolvedRoute(String publishTopic, String subscribeTopic) {
    }

    public static ResolvedRoute resolve(
            TopicCatalog catalog,
            FunctionRoute route,
            Map<String, String> topicOverrides,
            boolean write) {
        TopicCatalog effective = catalog.withOverrides(topicOverrides);
        FunctionRoute safe = route == null ? FunctionRoute.empty() : route;
        if (write) {
            return new ResolvedRoute(effective.resolvePublish(safe.publishTopicSlot()), null);
        }
        return new ResolvedRoute(null, effective.resolveSubscribe(safe.subscribeTopicSlot()));
    }
}
