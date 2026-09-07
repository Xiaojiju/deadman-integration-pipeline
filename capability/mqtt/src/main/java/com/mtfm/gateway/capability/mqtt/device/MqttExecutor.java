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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 南向 MQTT 执行器：共享 Channel 会话；Address 为 {@link TopicCatalog}。
 *
 * <p>bind 时订阅 catalog + READ 路由 topic；入站消息经 {@link PipelineIngress} 进入流水线。
 * execute 使用 {@link TopicRouteResolver#MQTT_PUBLISH_TOPIC_HINT}，READ 无发布 topic 时为 subscribe-only。
 *
 * <p>使用示例：
 * <pre>{@code
 * MqttExecutor executor = new MqttExecutor(transport);
 * executor.attach(pipeline, routeCatalog);
 * executor.bind(new DeviceEndpointBinding(deviceId, channelId, MqttCapability.TYPE, connection, address));
 * executor.execute(FunctionCommand.of(deviceId, "fn.open", Map.of("value", "open")));
 * }</pre>
 */
public final class MqttExecutor implements FunctionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MqttExecutor.class);

    private final MqttTransport transport;
    private volatile PipelineIngress ingress;
    private volatile MqttSubscribeRouteCatalog routeCatalog;
    private final ConcurrentHashMap<String, Bound> bindings = new ConcurrentHashMap<>();
    private final MqttTopicIndex topicIndex = new MqttTopicIndex();

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
            topicIndex.register(binding.channelId(), route.topic(), binding.deviceId(), route.functionId(), route.reply());
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
        List<MqttTopicIndex.TopicTarget> targets = topicIndex.lookup(channelId, topic);
        if (targets.isEmpty()) {
            return;
        }
        List<MqttTopicIndex.TopicTarget> mine = targets.stream()
                .filter(target -> deviceId.equals(target.deviceId()))
                .toList();
        if (mine.isEmpty()) {
            return;
        }
        MqttTopicIndex.TopicTarget reply = mine.stream().filter(MqttTopicIndex.TopicTarget::reply).findFirst().orElse(null);
        List<MqttTopicIndex.TopicTarget> listens = mine.stream().filter(target -> !target.reply()).toList();
        if (reply != null) {
            acceptInbound(reply.deviceId(), reply.functionId(), topic, payload, true);
        }
        for (MqttTopicIndex.TopicTarget listen : listens) {
            acceptInbound(listen.deviceId(), listen.functionId(), topic, payload, false);
        }
    }

    private void acceptInbound(String deviceId, String functionId, String topic, String payload, boolean reply) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("topic", topic);
        headers.put("functionId", functionId);
        headers.put("kind", "TELEMETRY");
        if (reply) {
            headers.put("mqtt.reply", "true");
        }
        ingress.acceptRaw(RawInbound.builder()
                .capabilityType(MqttCapability.TYPE)
                .deviceIdHint(deviceId)
                .text(payload)
                .headers(headers)
                .build());
    }

    private void dropBinding(Bound previous, String deviceId) {
        previous.handlers().forEach((topic, handler) ->
                transport.unsubscribe(previous.channelId(), topic, handler));
        topicIndex.unregisterDevice(previous.channelId(), deviceId);
        transport.release(previous.channelId());
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
        return MqttPayloadJson.stringify(arguments);
    }

    private record Bound(String channelId, TopicCatalog catalog, Map<String, BiConsumer<String, String>> handlers) {
        Bound {
            handlers = handlers == null ? Map.of() : Map.copyOf(handlers);
        }
    }

}
