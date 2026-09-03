package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.Channels;
import com.mtfm.gateway.spi.model.CommandResponse;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.MessageHeaders;
import com.mtfm.gateway.spi.model.MessagePriority;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;
import com.mtfm.gateway.spi.model.TelemetryEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudPublisherHubTest {

    @Test
    void snapshotStillWorksWithoutSinks() {
        CloudPublisher hub = new CloudPublisher();
        OutboundMessage message = response("req-1", "door-1");
        assertInstanceOf(PublishResult.Success.class, hub.publish(message));
        assertEquals(1, hub.responses().size());
        assertEquals("req-1", hub.responses().get(0).requestId());
    }

    @Test
    void fanoutDoesNotFailPublishWhenSinkThrows() {
        AtomicInteger calls = new AtomicInteger();
        CloudPublisher hub = new CloudPublisher(message -> {
            calls.incrementAndGet();
            throw new IllegalStateException("sink down");
        });
        assertInstanceOf(PublishResult.Success.class, hub.publish(response("req-2", "door-1")));
        assertEquals(1, calls.get());
        assertEquals(1, hub.snapshot().size());
    }

    @Test
    void mqttAndWebhookReceiveSameJsonShape() {
        InMemoryNorthboundMqttSession mqtt = new InMemoryNorthboundMqttSession();
        RecordingSink webhook = new RecordingSink();
        CloudPublisher hub = new CloudPublisher(
                new NorthboundMqttPublisher(mqtt),
                webhook);
        OutboundMessage response = response("req-3", "door-1");
        OutboundMessage telemetry = telemetry("door-1");
        hub.publish(response);
        hub.publish(telemetry);

        assertEquals(1, hub.responses().size());
        assertEquals(1, hub.telemetry().size());
        assertEquals(2, mqtt.snapshot().size());
        assertTrue(mqtt.snapshot().get(0).startsWith("gw/door-1/response|"));
        assertTrue(mqtt.snapshot().get(0).contains("\"requestId\":\"req-3\""));
        assertTrue(mqtt.snapshot().get(1).startsWith("gw/door-1/telemetry|"));
        assertEquals(2, webhook.bodies.size());
        assertTrue(webhook.bodies.get(0).contains("\"kind\":\"RESPONSE\""));
        assertTrue(webhook.bodies.get(1).contains("\"kind\":\"TELEMETRY\""));
    }

    @Test
    void inboundCommandUsesPortNotPipelineAccept() {
        List<NorthboundCommand> received = new CopyOnWriteArrayList<>();
        InMemoryNorthboundMqttSession session = new InMemoryNorthboundMqttSession();
        NorthboundMqttIngress ingress = new NorthboundMqttIngress(
                session, received::add, NorthboundTopics.DEFAULT_COMMAND);
        ingress.start();
        session.publish("gw/door-1/command",
                "{\"requestId\":\"req-in\",\"functionId\":\"fn.open\",\"arguments\":{\"lock\":\"open\"}}");
        assertEquals(1, received.size());
        assertEquals("door-1", received.get(0).deviceId());
        assertEquals("fn.open", received.get(0).functionId());
        assertEquals("req-in", received.get(0).requestId());
        assertEquals("open", received.get(0).arguments().get("lock"));
    }

    @Test
    void webhookPostsAndRetriesThenSucceeds() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            int n = hits.incrementAndGet();
            byte[] ignored = exchange.getRequestBody().readAllBytes();
            int status = n == 1 ? 500 : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            HttpWebhookPublisher webhook = new HttpWebhookPublisher(url, 2, Duration.ofSeconds(1));
            webhook.publish(response("req-wh", "door-1"));
            Instant deadline = Instant.now().plusSeconds(3);
            while (hits.get() < 2 && Instant.now().isBefore(deadline)) {
                Thread.sleep(20L);
            }
            webhook.close();
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    private static OutboundMessage response(String requestId, String deviceId) {
        CommandResponse body = CommandResponse.from(ExecutionResult.success(
                requestId, deviceId, "fn.open", Map.of("ok", true)));
        return new OutboundMessage(
                "msg-" + requestId, Channels.CLOUD, body, MessageHeaders.empty(), MessagePriority.HIGH, 0);
    }

    private static OutboundMessage telemetry(String deviceId) {
        TelemetryEvent body = new TelemetryEvent(null, deviceId, "fn.listen",
                com.mtfm.gateway.spi.model.Attributes.from(Map.of("seq", "orphan")));
        return new OutboundMessage(
                "tel-1", Channels.CLOUD, body, MessageHeaders.empty(), MessagePriority.NORMAL, 0);
    }

    private static final class RecordingSink implements NorthboundSink {
        private final List<String> bodies = new ArrayList<>();

        @Override
        public void publish(OutboundMessage message) {
            bodies.add(NorthboundJson.stringify(message));
        }
    }
}
