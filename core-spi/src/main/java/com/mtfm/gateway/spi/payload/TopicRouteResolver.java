package com.mtfm.gateway.spi.payload;

import java.util.Map;

/** 解析功能级 topic slot + 设备 catalog → 实际 MQTT topic。 */
public final class TopicRouteResolver {

    public static final String MQTT_PUBLISH_TOPIC_HINT = "mqtt.publishTopic";
    public static final String MQTT_SUBSCRIBE_TOPIC_HINT = "mqtt.subscribeTopic";
    public static final String MQTT_REPLY_TOPIC_HINT = "mqtt.replyTopic";
    public static final String MQTT_CORRELATION_PATH_HINT = "mqtt.correlationPath";
    public static final String MQTT_CORRELATION_COMMAND_PATH_HINT = "mqtt.correlationCommandPath";
    public static final String MQTT_REPLY_TIMEOUT_MS_HINT = "mqtt.replyTimeoutMs";

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
        String publish = null;
        if (safe.publishTopicSlot() != null && !safe.publishTopicSlot().isBlank()) {
            publish = effective.resolvePublish(safe.publishTopicSlot());
        }
        return new ResolvedRoute(publish, effective.resolveSubscribe(safe.subscribeTopicSlot()));
    }
}
