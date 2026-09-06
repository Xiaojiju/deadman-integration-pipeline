package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.AccessPermission;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.property.ValueAccessType;
import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MqttReadInboundPluginTest {

    @Test
    void listenMapsPickedFieldsAndSkipsMissing() {
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", listen(
                new WriteFieldOption(
                        "params.1",
                        "状态",
                        "string",
                        "string",
                        true,
                        List.of(new ValueOption("0", "closed", "关", "string", "string", false),
                                new ValueOption("1", "open", "开", "string", "string", false)),
                        "none",
                        null,
                        "caller",
                        null,
                        "door"),
                new WriteFieldOption(
                        "params.9",
                        "不存在",
                        "string",
                        "string",
                        true,
                        List.of(),
                        "none",
                        null,
                        "caller",
                        null,
                        "missing")));
        MqttReadInboundPlugin plugin = new MqttReadInboundPlugin(catalog);
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .capabilityType(MqttCapability.TYPE)
                .deviceId("door-1")
                .functionId("fn.listen")
                .payload(Map.of("text", "{\"params\":[\"F123\",\"1\"]}"))
                .build();

        InboundApplyResult.Continue cont = assertInstanceOf(InboundApplyResult.Continue.class, plugin.apply(draft));
        assertEquals("open", cont.draft().payload().values().get("door"));
        assertEquals(1, cont.draft().payload().values().size());
    }

    @Test
    void listenDropsWhenNoConfiguredFieldPicked() {
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", listen(new WriteFieldOption(
                "humidity", "湿度", "int", "int", false, List.of(), "none", null)));
        MqttReadInboundPlugin plugin = new MqttReadInboundPlugin(catalog);
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .capabilityType(MqttCapability.TYPE)
                .deviceId("door-1")
                .functionId("fn.listen")
                .payload(Map.of("text", "{\"temp\":25}"))
                .build();

        assertInstanceOf(InboundApplyResult.Drop.class, plugin.apply(draft));
    }

    @Test
    void replyKeepsFullJsonForCorrelation() {
        ReplyCatalog catalog = new ReplyCatalog();
        catalog.put("door-1", new FunctionDef(
                "fn.open",
                "WRITE",
                AccessPermission.WRITE.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(new WriteFieldOption(
                        "params.1", "成败", "string", "string", true, List.of(),
                        "none", null, "caller", null, "success")),
                List.of(),
                PayloadEncoding.JSON,
                "response",
                "params.0",
                "params.1",
                2000,
                null,
                false));
        MqttReadInboundPlugin plugin = new MqttReadInboundPlugin(catalog);
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .kind(EnvelopeKind.TELEMETRY)
                .capabilityType(MqttCapability.TYPE)
                .deviceId("door-1")
                .functionId("fn.open")
                .payload(Map.of("text", "{\"params\":[\"F123\",\"1\"]}"))
                .build();

        InboundApplyResult.Continue cont = assertInstanceOf(InboundApplyResult.Continue.class, plugin.apply(draft));
        assertEquals("true", cont.draft().headers().get("mqtt.reply").orElse(""));
        assertEquals(List.of("F123", "1"), cont.draft().payload().values().get("params"));
    }

    private static FunctionDef listen(WriteFieldOption... fields) {
        return new FunctionDef(
                "fn.listen",
                "READ",
                AccessPermission.READ.code(),
                null,
                List.of(),
                ValueAccessType.STRUCT,
                List.of(),
                List.of(),
                List.of(fields),
                List.of(),
                PayloadEncoding.JSON);
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
