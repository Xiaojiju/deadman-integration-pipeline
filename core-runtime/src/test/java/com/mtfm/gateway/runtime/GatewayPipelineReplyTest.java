package com.mtfm.gateway.runtime;

import com.mtfm.gateway.spi.capability.Driver;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.RawInbound;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayPipelineReplyTest {

    private GatewayPipeline pipeline;

    @AfterEach
    void tearDown() {
        if (pipeline != null) {
            pipeline.close();
        }
    }

    @Test
    void acceptedThenMatchedTelemetryBecomesCommandResponse() throws Exception {
        AcceptingExecutor executor = new AcceptingExecutor();
        StubPublisher publisher = new StubPublisher();
        InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog().allow("door-1", "fn.open");
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        pipeline.register(new CapabilityDescriptor("PROTO", List.of(), List.of()), new StubDriver(), executor);
        pipeline.register("door-1", "PROTO");
        pipeline.register(publisher);
        pipeline.start();

        ExecutionResult accepted = pipeline.submit(FunctionCommand.of(
                "req-open-1",
                "door-1",
                "fn.open",
                Map.of("seq", "req-open-1"),
                Map.of(
                        TopicRouteResolver.MQTT_REPLY_TOPIC_HINT, "ydlink/F1111/response",
                        TopicRouteResolver.MQTT_CORRELATION_PATH_HINT, "seq")))
                .get(3, TimeUnit.SECONDS);
        assertEquals(ExecutionStatus.ACCEPTED, accepted.status());

        assertTrue(pipeline.accept(EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .deviceId("door-1")
                .functionId("fn.open")
                .capabilityType("PROTO")
                .payload(Map.of("seq", "req-open-1", "ok", true))
                .headers(Map.of("mqtt.reply", "true", "mqtt.correlationPath", "seq"))
                .build()));
        assertTrue(awaitResponse(publisher, Duration.ofSeconds(2)));

        List<CommandResponse> responses = publisher.published.stream()
                .map(OutboundMessage::body)
                .filter(CommandResponse.class::isInstance)
                .map(CommandResponse.class::cast)
                .toList();
        assertEquals(1, responses.size());
        assertEquals(ExecutionStatus.SUCCESS, responses.get(0).status());
        assertEquals("req-open-1", responses.get(0).requestId());
    }

    @Test
    void correlatesDeviceCodeWithArrayIndexPath() throws Exception {
        AcceptingExecutor executor = new AcceptingExecutor();
        StubPublisher publisher = new StubPublisher();
        InMemoryFunctionCatalog catalog = new InMemoryFunctionCatalog().allow("F123", "fn.open");
        pipeline = GatewayPipeline.builder().functionCatalog(catalog).build();
        pipeline.register(new CapabilityDescriptor("PROTO", List.of(), List.of()), new StubDriver(), executor);
        pipeline.register("F123", "PROTO");
        pipeline.register(publisher);
        pipeline.start();

        ExecutionResult accepted = pipeline.submit(FunctionCommand.of(
                "req-ignored",
                "F123",
                "fn.open",
                Map.of("params", List.of("open")),
                Map.of(
                        TopicRouteResolver.MQTT_REPLY_TOPIC_HINT, "ydlink/dev/execute_response",
                        TopicRouteResolver.MQTT_CORRELATION_COMMAND_PATH_HINT, "$deviceCode",
                        TopicRouteResolver.MQTT_CORRELATION_PATH_HINT, "params.0")))
                .get(3, TimeUnit.SECONDS);
        assertEquals(ExecutionStatus.ACCEPTED, accepted.status());

        assertTrue(pipeline.accept(EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .deviceId("F123")
                .functionId("fn.open")
                .capabilityType("PROTO")
                .payload(Map.of("params", List.of("F123", "1")))
                .headers(Map.of(
                        "mqtt.reply", "true",
                        "mqtt.correlationPath", "params.0"))
                .build()));
        assertTrue(awaitResponse(publisher, Duration.ofSeconds(2)));

        List<CommandResponse> responses = publisher.published.stream()
                .map(OutboundMessage::body)
                .filter(CommandResponse.class::isInstance)
                .map(CommandResponse.class::cast)
                .toList();
        assertEquals(1, responses.size());
        assertEquals(ExecutionStatus.SUCCESS, responses.get(0).status());
        assertEquals("req-ignored", responses.get(0).requestId());
    }

    private static boolean awaitResponse(StubPublisher publisher, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            boolean hit = publisher.published.stream().map(OutboundMessage::body)
                    .anyMatch(CommandResponse.class::isInstance);
            if (hit) {
                return true;
            }
            Thread.sleep(20L);
        }
        return publisher.published.stream().map(OutboundMessage::body)
                .anyMatch(CommandResponse.class::isInstance);
    }

    private static final class StubDriver implements Driver {
        @Override
        public String capabilityType() {
            return "PROTO";
        }

        @Override
        public EnvelopeDraft decode(RawInbound raw) {
            return EnvelopeDraft.builder()
                    .kind(EnvelopeKind.TELEMETRY)
                    .deviceId(raw.deviceIdHint())
                    .capabilityType(capabilityType())
                    .payload(Attributes.empty())
                    .build();
        }
    }

    private static final class AcceptingExecutor implements FunctionExecutor {
        @Override
        public String capabilityType() {
            return "PROTO";
        }

        @Override
        public ExecutionResult execute(FunctionCommand command) {
            return ExecutionResult.accepted(command.requestId(), command.deviceId(), command.functionId(),
                    Map.of("queued", true));
        }
    }

    private static final class StubPublisher implements Publisher {
        private final CopyOnWriteArrayList<OutboundMessage> published = new CopyOnWriteArrayList<>();

        @Override
        public String channel() {
            return Channels.CLOUD;
        }

        @Override
        public PublishResult publish(OutboundMessage message) {
            published.add(message);
            return PublishResult.success();
        }
    }
}
