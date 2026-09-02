package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.payload.FramePacker;
import com.mtfm.gateway.spi.payload.PayloadDisassembler;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.plugin.InboundPlugin;

import java.util.Map;
import java.util.Optional;

/**
 * MQTT READ 入站：JSON 按 readFields 抽点；HEX/BINARY 按 byteLength 切片。
 */
public final class MqttReadInboundPlugin implements InboundPlugin {

    private final FunctionCatalog catalog;

    public MqttReadInboundPlugin(FunctionCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "mqtt-read";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean support(EnvelopeDraft draft) {
        return draft != null
                && MqttCapability.TYPE.equalsIgnoreCase(draft.capabilityType())
                && draft.kind() == EnvelopeKind.TELEMETRY
                && draft.functionId() != null
                && !draft.functionId().isBlank();
    }

    @Override
    public InboundApplyResult apply(EnvelopeDraft draft) {
        Optional<FunctionDef> def = catalog.find(draft.deviceId(), draft.functionId());
        if (def.isEmpty()) {
            return InboundApplyResult.continueWith(draft);
        }
        FunctionDef function = def.get();
        PayloadEncoding encoding = function.payloadEncoding();
        if (encoding != null && encoding.isFramed()) {
            String text = rawText(draft);
            Map<String, Object> points = FramePacker.unpack(text, function.readFields(), encoding);
            return InboundApplyResult.continueWith(
                    draft.withPayload(Attributes.from(points)).appendTrace(name(), "unpack-" + encoding.wire()));
        }
        Map<String, Object> json = draft.payload().values();
        if (json.isEmpty() || json.containsKey("text")) {
            String text = draft.payload().get("text").map(String::valueOf).orElse("");
            json = MqttPayloadJson.parseObject(text);
        }
        Map<String, Object> points = PayloadDisassembler.disassemble(json, function.readFields());
        Attributes payload = Attributes.from(points);
        return InboundApplyResult.continueWith(
                draft.withPayload(payload).appendTrace(name(), "disassemble"));
    }

    private static String rawText(EnvelopeDraft draft) {
        String text = draft.payload().get("text").map(String::valueOf).orElse("");
        if (!text.isBlank()) {
            return text;
        }
        Object value = draft.payload().values().get("_value");
        return value == null ? "" : String.valueOf(value);
    }
}
