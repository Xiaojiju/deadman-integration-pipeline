package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.RawInbound;
import com.mtfm.gateway.spi.payload.TopicCatalog;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.port.MqttSubscribeRoute;
import com.mtfm.gateway.spi.port.MqttSubscribeRouteCatalog;
import com.mtfm.gateway.spi.port.PipelineIngress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 南向 MQTT 执行器：共享 Channel 会话；Address 为 {@link TopicCatalog}。
 *
 * <p>bind 时订阅 catalog + READ 路由 topic；入站消息经 {@link PipelineIngress} 进入流水线。
 * execute 使用 {@link TopicRouteResolver#MQTT_PUBLISH_TOPIC_HINT}，READ 无发布 topic 时为 subscribe-only。
 */
public final class MqttExecutor implements FunctionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MqttExecutor.class);

    private final MqttTransport transport;
    private volatile PipelineIngress ingress;
    private volatile MqttSubscribeRouteCatalog routeCatalog;
    private final ConcurrentHashMap<String, Bound> bindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TopicTarget>> topicIndex = new ConcurrentHashMap<>();

    public MqttExecutor(MqttTransport transport) {
        this.transport = transport;
    }

    /** 装配完成后注入（避免与 GatewayPipeline 构造循环依赖）。 */
    public void attach(PipelineIngress ingress, MqttSubscribeRouteCatalog routeCatalog) {
        this.ingress = ingress;
        this.routeCatalog = routeCatalog;
    }

    @Override
    public String capabilityType() {
        return MqttCapability.TYPE;
    }

    @Override
    public void bind(DeviceEndpointBinding binding) {
        TopicCatalog catalog = TopicCatalog.fromAddressMap(binding.address().values());
        Bound previous = bindings.remove(binding.deviceId());
        if (previous != null) {
            dropBinding(previous, binding.deviceId());
        }
        transport.retain(binding.channelId(), MqttBrokerConnection.fromAttributes(binding.connection()));

        Set<String> topics = new LinkedHashSet<>(catalog.allTopics());
        List<MqttSubscribeRoute> readRoutes = routeCatalog == null
                ? List.of()
                : routeCatalog.routesForDevice(binding.deviceId(), binding.address().values());
        for (MqttSubscribeRoute route : readRoutes) {
            topics.add(route.topic());
            registerTopic(binding.channelId(), route.topic(), binding.deviceId(), route.functionId(), route.reply());
        }

        Map<String, BiConsumer<String, String>> handlers = new LinkedHashMap<>();
        String deviceId = binding.deviceId();
        String channelId = binding.channelId();
        for (String topic : topics) {
            BiConsumer<String, String> handler = (receivedTopic, payload) ->
                    dispatchInbound(channelId, deviceId, receivedTopic, payload);
            handlers.put(topic, handler);
            transport.subscribe(channelId, topic, handler);
        }
        bindings.put(deviceId, new Bound(channelId, catalog, Map.copyOf(handlers)));
    }

    @Override
    public boolean unbind(String deviceId) {
        Bound previous = bindings.remove(deviceId);
        if (previous == null) {
            return false;
        }
        dropBinding(previous, deviceId);
        return true;
    }

    @Override
    public ExecutionResult execute(FunctionCommand command) {
        Bound bound = bindings.get(command.deviceId());
        if (bound == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "未绑定端点: " + command.deviceId(), false));
        }
        if (command.deliveryHints().get("mqtt.subscribeOnly")
                .map(value -> "true".equalsIgnoreCase(String.valueOf(value)))
                .orElse(false)) {
            LOG.info("MQTT 订阅只读，跳过发布 deviceId={} functionId={} hints={}",
                    command.deviceId(), command.functionId(), command.deliveryHints().values());
            return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                    Map.of("mode", "subscribe-only"));
        }
        String topic = command.deliveryHints()
                .get(TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> bound.catalog().resolvePublish(null));
        String payload = toPayload(command.arguments().values());
        LOG.info("MQTT 发布 deviceId={} functionId={} channelId={} topic={} payload={}",
                command.deviceId(), command.functionId(), bound.channelId(), topic, payload);
        transport.publish(bound.channelId(), topic, payload);
        boolean awaitingReply = command.deliveryHints()
                .get(TopicRouteResolver.MQTT_REPLY_TOPIC_HINT)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .isPresent();
        if (awaitingReply) {
            return ExecutionResult.accepted(command.requestId(), command.deviceId(), command.functionId(),
                    Map.of("topic", topic));
        }
        return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                Map.of("topic", topic));
    }

    void dispatchInbound(String channelId, String deviceId, String topic, String payload) {
        if (ingress == null) {
            return;
        }
        List<TopicTarget> targets = lookupTargets(channelId, topic);
        if (targets.isEmpty()) {
            return;
        }
        List<TopicTarget> mine = targets.stream()
                .filter(target -> deviceId.equals(target.deviceId()))
                .toList();
        if (mine.isEmpty()) {
            return;
        }
        TopicTarget reply = mine.stream().filter(TopicTarget::reply).findFirst().orElse(null);
        TopicTarget listen = mine.stream().filter(target -> !target.reply()).findFirst().orElse(null);
        TopicTarget chosen = reply != null ? reply : listen;
        if (chosen == null) {
            return;
        }
        java.util.Map<String, String> headers = new LinkedHashMap<>();
        headers.put("topic", topic);
        headers.put("functionId", chosen.functionId());
        headers.put("kind", "TELEMETRY");
        if (reply != null) {
            headers.put("mqtt.reply", "true");
            if (listen != null && !listen.functionId().equals(reply.functionId())) {
                headers.put("mqtt.listenFunctionId", listen.functionId());
            }
        }
        ingress.acceptRaw(RawInbound.builder()
                .capabilityType(MqttCapability.TYPE)
                .deviceIdHint(chosen.deviceId())
                .text(payload)
                .headers(headers)
                .build());
    }

    private void registerTopic(String channelId, String topic, String deviceId, String functionId, boolean reply) {
        topicIndex.computeIfAbsent(indexKey(channelId, topic), key -> new CopyOnWriteArrayList<>())
                .add(new TopicTarget(deviceId, functionId, reply));
    }

    private void dropBinding(Bound previous, String deviceId) {
        previous.handlers().forEach((topic, handler) ->
                transport.unsubscribe(previous.channelId(), topic, handler));
        unregisterDevice(previous.channelId(), deviceId);
        transport.release(previous.channelId());
    }

    private void unregisterDevice(String channelId, String deviceId) {
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

    private List<TopicTarget> lookupTargets(String channelId, String topic) {
        CopyOnWriteArrayList<TopicTarget> direct = topicIndex.get(indexKey(channelId, topic));
        return direct == null ? List.of() : List.copyOf(direct);
    }

    private static String indexKey(String channelId, String topic) {
        return channelId + "\0" + topic;
    }

    /** 无参 → 空串；FILL_ROOT 标量 → 原串；否则 JSON。 */
    static String toPayload(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        if (arguments.size() == 1 && arguments.containsKey("_value")) {
            Object scalar = arguments.get("_value");
            return scalar == null ? "" : String.valueOf(scalar);
        }
        return toJson(arguments);
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(toJson(item));
            }
            return sb.append(']').toString();
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(toJson(java.lang.reflect.Array.get(value, i)));
            }
            return sb.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private record Bound(String channelId, TopicCatalog catalog, Map<String, BiConsumer<String, String>> handlers) {
        Bound {
            handlers = handlers == null ? Map.of() : Map.copyOf(handlers);
        }
    }

    private record TopicTarget(String deviceId, String functionId, boolean reply) {
    }
}
