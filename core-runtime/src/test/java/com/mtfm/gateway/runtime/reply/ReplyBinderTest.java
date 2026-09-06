package com.mtfm.gateway.runtime.reply;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.Direction;
import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.MessageHeaders;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyBinderTest {

    @Test
    void bindMatchesCorrelationPathFromCatalog() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-open-1", "door-1", "fn.open", "req-open-1", "ok", Instant.now().plusSeconds(5))));
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", openFunction());
        ReplyBinder binder = new ReplyBinder(catalog, waiter);

        Optional<com.mtfm.gateway.spi.model.ExecutionResult> matched = binder.bind(envelope(
                "door-1",
                "fn.open",
                Map.of("seq", "req-open-1", "ok", true),
                Map.of("mqtt.reply", "true")));

        assertTrue(matched.isPresent());
        assertEquals(ExecutionStatus.SUCCESS, matched.get().status());
        assertEquals("req-open-1", matched.get().requestId());
        waiter.close();
    }

    @Test
    void bindMatchesArrayIndexAndProjectsNamedValues() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-3", "F123", "fn.open", "F123", "params.1", Instant.now().plusSeconds(5))));
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("F123", new FunctionDef(
                "fn.open",
                "WRITE",
                AccessPermission.WRITE.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(
                        new WriteFieldOption(
                                "params.1", "成败", "string", "string", true, List.of(),
                                "none", null, "caller", null, "success"),
                        new WriteFieldOption(
                                "params.2", "说明", "string", "string", true, List.of(),
                                "none", null, "caller", null, "message")),
                List.of(),
                PayloadEncoding.JSON,
                "ydlink/dev/response",
                "params.0",
                "params.1",
                2000,
                null,
                false));
        ReplyBinder binder = new ReplyBinder(catalog, waiter);

        Optional<com.mtfm.gateway.spi.model.ExecutionResult> matched = binder.bind(envelope(
                "F123",
                "fn.open",
                Map.of("params", List.of("F123", "1", "opened")),
                Map.of("mqtt.reply", "true", "mqtt.correlationPath", "params.0")));

        assertTrue(matched.isPresent());
        assertEquals(ExecutionStatus.SUCCESS, matched.get().status());
        assertEquals("1", matched.get().data().values().get("success"));
        assertEquals("opened", matched.get().data().values().get("message"));
        waiter.close();
    }

    @Test
    void bindMapsOptionValueToNorthboundMappingValue() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-4", "F123", "fn.open", "F123", "params.1", Instant.now().plusSeconds(5))));
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("F123", new FunctionDef(
                "fn.open",
                "WRITE",
                AccessPermission.WRITE.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(new WriteFieldOption(
                        "params.1",
                        "成败",
                        "string",
                        "string",
                        true,
                        List.of(new ValueOption("0", "failed", "失败", "string", "string", false),
                                new ValueOption("1", "ok", "成功", "string", "string", false)),
                        "none",
                        null,
                        "caller",
                        null,
                        "success")),
                List.of(),
                PayloadEncoding.JSON,
                "ydlink/dev/response",
                "params.0",
                null,
                2000,
                null,
                false));
        ReplyBinder binder = new ReplyBinder(catalog, waiter);

        Optional<com.mtfm.gateway.spi.model.ExecutionResult> matched = binder.bind(envelope(
                "F123",
                "fn.open",
                Map.of("params", List.of("F123", "1", "opened")),
                Map.of("mqtt.reply", "true")));

        assertTrue(matched.isPresent());
        assertEquals(ExecutionStatus.SUCCESS, matched.get().status());
        assertEquals("ok", matched.get().data().values().get("success"));
        waiter.close();
    }

    @Test
    void bindTreatsReplyFieldZeroAsFailureWithoutResultPath() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-5", "F123", "fn.open", "F123", null, Instant.now().plusSeconds(5))));
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("F123", new FunctionDef(
                "fn.open",
                "WRITE",
                AccessPermission.WRITE.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(new WriteFieldOption(
                        "params.1",
                        "成败",
                        "string",
                        "string",
                        true,
                        List.of(new ValueOption("0", "failed", "失败", "string", "string", false),
                                new ValueOption("1", "ok", "成功", "string", "string", false)),
                        "none",
                        null,
                        "caller",
                        null,
                        "success")),
                List.of(),
                PayloadEncoding.JSON,
                "ydlink/dev/response",
                "params.0",
                null,
                2000,
                null,
                false));
        ReplyBinder binder = new ReplyBinder(catalog, waiter);

        Optional<com.mtfm.gateway.spi.model.ExecutionResult> matched = binder.bind(envelope(
                "F123",
                "fn.open",
                Map.of("params", List.of("F123", "0", "busy")),
                Map.of("mqtt.reply", "true")));

        assertTrue(matched.isPresent());
        assertEquals(ExecutionStatus.FAILED, matched.get().status());
        assertEquals("failed", matched.get().data().values().get("success"));
        waiter.close();
    }

    @Test
    void bindFallsBackToSeqWhenCatalogPathMissing() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-2", "door-1", "fn.open", "req-2", null, Instant.now().plusSeconds(5))));
        ReplyBinder binder = new ReplyBinder((deviceId, functionId) -> Optional.empty(), waiter);

        Optional<com.mtfm.gateway.spi.model.ExecutionResult> matched = binder.bind(envelope(
                "door-1",
                "fn.open",
                Map.of("seq", "req-2", "ok", true),
                Map.of()));

        assertTrue(matched.isPresent());
        assertEquals("req-2", matched.get().requestId());
        waiter.close();
    }

    private static Envelope envelope(
            String deviceId, String functionId, Map<String, Object> payload, Map<String, String> headers) {
        return new Envelope(
                "env-1",
                null,
                Direction.INBOUND,
                EnvelopeKind.TELEMETRY,
                deviceId,
                functionId,
                "MQTT",
                Attributes.from(payload),
                MessageHeaders.from(headers),
                List.of(),
                null,
                Instant.now(),
                null);
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
                List.of(new WriteFieldOption("seq", "", "string", "string", false, List.of()),
                        new WriteFieldOption("ok", "", "string", "string", false, List.of())),
                List.of(),
                PayloadEncoding.JSON,
                "response",
                "seq",
                "ok",
                2000,
                null,
                false);
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
