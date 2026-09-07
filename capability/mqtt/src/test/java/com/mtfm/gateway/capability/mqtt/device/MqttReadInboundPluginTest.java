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
                WriteFieldOption.builder("params.1")
                        .description("状态")
                        .ignoreRequest(true)
                        .options(List.of(
                                new ValueOption("0", "closed", "关", "string", "string", false),
                                new ValueOption("1", "open", "开", "string", "string", false)))
                        .source("caller")
                        .callerField("door")
                        .build(),
                WriteFieldOption.builder("params.9")
                        .description("不存在")
                        .ignoreRequest(true)
                        .source("caller")
                        .callerField("missing")
                        .build()));
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
        catalog.put("door-1", listen(WriteFieldOption.builder("humidity")
                .description("湿度")
                .accessDataType("int")
                .transformDataType("int")
                .build()));
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
        catalog.put("door-1", FunctionDef.builder("fn.open")
                .accessType("WRITE")
                .accessPermission(AccessPermission.WRITE.code())
                .writeAccessType(ValueAccessType.STRUCT)
                .readFields(List.of(WriteFieldOption.builder("params.1")
                        .description("成败")
                        .ignoreRequest(true)
                        .source("caller")
                        .callerField("success")
                        .build()))
                .payloadEncoding(PayloadEncoding.JSON)
                .reply(FunctionDef.ReplySpec.of("response", "params.0", 2000))
                .build());
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
        return FunctionDef.builder("fn.listen")
                .accessType("READ")
                .accessPermission(AccessPermission.READ.code())
                .writeAccessType(ValueAccessType.STRUCT)
                .readFields(List.of(fields))
                .payloadEncoding(PayloadEncoding.JSON)
                .build();
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
