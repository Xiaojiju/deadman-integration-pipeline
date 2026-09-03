package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.capability.cloud.CloudPublisher;
import com.mtfm.gateway.runtime.GatewayPipeline;
import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.TelemetryEvent;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.port.MqttSubscribeRoute;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttReplyLoopTest {

    private GatewayPipeline pipeline;

    @AfterEach
    void tearDown() {
        if (pipeline != null) {
            pipeline.close();
        }
    }

    @Test
    void acceptedThenMatchedReplyBecomesCommandResponse() throws Exception {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        CloudPublisher publisher = new CloudPublisher();
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", openFunction());
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        MqttExecutor executor = new MqttExecutor(transport);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), executor);
        pipeline.register(new MqttReadInboundPlugin(catalog));
        pipeline.register(publisher);
        pipeline.register("door-1", MqttCapability.TYPE);
        pipeline.start();
        executor.attach(pipeline, (deviceCode, address) -> List.of(
                new MqttSubscribeRoute("ydlink/F1111/response", "fn.open", true)));
        executor.bind(binding("door-1", "ch-1", Map.of(
                "default_pub", "ydlink/F1111/execute",
                "default_sub", "ydlink/F1111/response",
                "topics", Map.of("response", "ydlink/F1111/response"))));

        ExecutionResult accepted = pipeline.submit(FunctionCommand.of(
                "req-open-1",
                "door-1",
                "fn.open",
                Map.of("seq", "req-open-1"),
                Map.of(
                        TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT, "ydlink/F1111/execute",
                        TopicRouteResolver.MQTT_REPLY_TOPIC_HINT, "ydlink/F1111/response",
                        TopicRouteResolver.MQTT_CORRELATION_PATH_HINT, "seq",
                        TopicRouteResolver.MQTT_RESULT_PATH_HINT, "ok")))
                .get(3, TimeUnit.SECONDS);
        assertEquals(ExecutionStatus.ACCEPTED, accepted.status());
        assertEquals("req-open-1", accepted.requestId());

        assertTrue(transport.handlerCount("ch-1", "ydlink/F1111/response") > 0);
        transport.publish("ch-1", "ydlink/F1111/response", "{\"seq\":\"req-open-1\",\"ok\":true}");
        assertTrue(awaitResponse(publisher, Duration.ofSeconds(2)),
                () -> "stats=" + pipeline.stats().get("drop.telemetry_reject")
                        + "/" + pipeline.stats().get("drop.decode")
                        + " published=" + publisher.snapshot().size());

        List<CommandResponse> responses = publisher.responses();
        assertEquals(1, responses.size());
        assertEquals(ExecutionStatus.SUCCESS, responses.get(0).status());
        assertEquals("req-open-1", responses.get(0).requestId());
        assertEquals("fn.open", responses.get(0).functionId());
    }

    @Test
    void unmatchedReplyOnSharedTopicGoesToListen() throws Exception {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        CloudPublisher publisher = new CloudPublisher();
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", openFunction());
        catalog.put("door-1", listenFunction());
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        MqttExecutor executor = new MqttExecutor(transport);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), executor);
        pipeline.register(new MqttReadInboundPlugin(catalog));
        pipeline.register(publisher);
        pipeline.register("door-1", MqttCapability.TYPE);
        pipeline.start();
        executor.attach(pipeline, (deviceCode, address) -> List.of(
                new MqttSubscribeRoute("ydlink/F1111/response", "fn.open", true),
                new MqttSubscribeRoute("ydlink/F1111/response", "fn.listen", false)));
        executor.bind(binding("door-1", "ch-1", Map.of(
                "default_pub", "ydlink/F1111/execute",
                "default_sub", "ydlink/F1111/response")));

        transport.publish("ch-1", "ydlink/F1111/response", "{\"seq\":\"orphan\",\"ok\":true}");
        assertTrue(awaitTelemetry(publisher, Duration.ofSeconds(2)),
                () -> "stats=" + pipeline.stats().get("drop.telemetry_reject")
                        + "/" + pipeline.stats().get("drop.decode")
                        + " published=" + publisher.snapshot().size());

        assertTrue(publisher.responses().isEmpty());
        List<TelemetryEvent> telemetry = publisher.telemetry();
        assertEquals(1, telemetry.size());
        assertEquals("fn.listen", telemetry.get(0).functionId());
    }

    @Test
    void replyTimeoutPublishesTimeoutResponse() throws Exception {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        CloudPublisher publisher = new CloudPublisher();
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", openFunction());
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        MqttExecutor executor = new MqttExecutor(transport);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), executor);
        pipeline.register(publisher);
        pipeline.register("door-1", MqttCapability.TYPE);
        pipeline.start();
        executor.attach(pipeline, (deviceCode, address) -> List.of(
                new MqttSubscribeRoute("ydlink/F1111/response", "fn.open", true)));
        executor.bind(binding("door-1", "ch-1", Map.of(
                "default_pub", "ydlink/F1111/execute",
                "default_sub", "ydlink/F1111/response")));

        ExecutionResult accepted = pipeline.submit(FunctionCommand.of(
                "req-timeout",
                "door-1",
                "fn.open",
                Map.of(),
                Map.of(
                        TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT, "ydlink/F1111/execute",
                        TopicRouteResolver.MQTT_REPLY_TOPIC_HINT, "ydlink/F1111/response",
                        TopicRouteResolver.MQTT_REPLY_TIMEOUT_MS_HINT, 50)))
                .get(3, TimeUnit.SECONDS);
        assertEquals(ExecutionStatus.ACCEPTED, accepted.status());

        assertTrue(awaitResponse(publisher, Duration.ofSeconds(2)));
        CommandResponse response = publisher.responses().get(0);
        assertEquals(ExecutionStatus.TIMEOUT, response.status());
        assertEquals("req-timeout", response.requestId());
    }

    @Test
    void syncExecuteWithoutReplyHintStillSucceeds() throws Exception {
        InMemoryMqttTransport transport = new InMemoryMqttTransport();
        CloudPublisher publisher = new CloudPublisher();
        pipeline = GatewayPipeline.builder().build();
        MqttExecutor executor = new MqttExecutor(transport);
        pipeline.register(MqttCapability.DESCRIPTOR, new MqttDriver(), executor);
        pipeline.register(publisher);
        pipeline.register("door-1", MqttCapability.TYPE);
        pipeline.start();
        executor.bind(binding("door-1", "ch-1", Map.of("default_pub", "ydlink/F1111/execute")));

        ExecutionResult result = pipeline.submit(FunctionCommand.of(
                "req-sync",
                "door-1",
                "fn.open",
                Map.of("action", "open"),
                Map.of(TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT, "ydlink/F1111/execute")))
                .get(3, TimeUnit.SECONDS);
        assertTrue(pipeline.awaitIdle(Duration.ofSeconds(2)));
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals(1, publisher.responses().size());
        assertEquals(ExecutionStatus.SUCCESS, publisher.responses().get(0).status());
    }

    private static boolean awaitResponse(CloudPublisher publisher, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!publisher.responses().isEmpty()) {
                return true;
            }
            Thread.sleep(20L);
        }
        return !publisher.responses().isEmpty();
    }

    private static boolean awaitTelemetry(CloudPublisher publisher, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!publisher.telemetry().isEmpty()) {
                return true;
            }
            Thread.sleep(20L);
        }
        return !publisher.telemetry().isEmpty();
    }

    private static FunctionDef openFunction() {
        return new FunctionDef(
                "fn.open",
                "WRITE",
                AccessPermission.WRITE.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(
                        field("seq"),
                        field("ok")),
                List.of(),
                PayloadEncoding.JSON,
                "response",
                "seq",
                "ok",
                2000,
                null,
                false);
    }

    private static FunctionDef listenFunction() {
        return new FunctionDef(
                "fn.listen",
                "READ",
                AccessPermission.READ.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(field("seq"), field("ok")),
                List.of(),
                PayloadEncoding.JSON);
    }

    private static WriteFieldOption field(String name) {
        return new WriteFieldOption(name, "", "string", "string", false, List.of());
    }

    private static DeviceEndpointBinding binding(String deviceId, String channelId, Map<String, Object> address) {
        return new DeviceEndpointBinding(
                deviceId,
                channelId,
                MqttCapability.TYPE,
                Attributes.from(Map.of("broker", "mqtt://localhost")),
                Attributes.from(address));
    }

    private static final class ReplyCatalog implements FunctionCatalog {
        private final ConcurrentHashMap<String, FunctionDef> defs = new ConcurrentHashMap<>();

        void put(String deviceId, FunctionDef def) {
            defs.put(deviceId + "/" + def.functionId(), def);
        }

        @Override
        public Optional<FunctionDef> find(String deviceId, String functionId) {
            return Optional.ofNullable(defs.get(deviceId + "/" + functionId));
        }
    }
}
