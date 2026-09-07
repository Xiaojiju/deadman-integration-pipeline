package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.model.MessageHeaders;
import com.mtfm.gateway.spi.payload.FramePacker;
import com.mtfm.gateway.spi.payload.PayloadDisassembler;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.plugin.InboundPlugin;

import java.util.Map;
import java.util.Optional;

/**
 * MQTT READ 入站：JSON 按 readFields 拾取并映射；HEX/BINARY 按 byteLength 切片。
 * 配了拾取字段但一个都没有时静默丢弃，避免把整段报文当成该功能遥测。
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
        MessageHeaders headers = draft.headers();
        if (function.awaitsReply()) {
            headers = headers.with("mqtt.reply", "true");
            if (function.correlationPath() != null) {
                headers = headers.with("mqtt.correlationPath", function.correlationPath());
            }
        }
        PayloadEncoding encoding = function.payloadEncoding();
        if (encoding != null && encoding.isFramed()) {
            String text = rawText(draft);
            Map<String, Object> unpacked = FramePacker.unpack(text, function.readFields(), encoding);
            if (function.awaitsReply()) {
                return InboundApplyResult.continueWith(
                        draft.withPayload(Attributes.from(unpacked)).withHeaders(headers)
                                .appendTrace(name(), "unpack-" + encoding.wire()));
            }
            Map<String, Object> points = PayloadDisassembler.project(
                    unpacked, function.readFields(), function.readValueOptions(),
                    function.scaleOp(), function.scaleOperand());
            if (skipUnpicked(function, points)) {
                return InboundApplyResult.drop();
            }
            return InboundApplyResult.continueWith(
                    draft.withPayload(Attributes.from(points)).withHeaders(headers)
                            .appendTrace(name(), "unpack-" + encoding.wire()));
        }
        Map<String, Object> json = draft.payload().values();
        if (json.size() == 1 && json.containsKey("text")) {
            String text = draft.payload().get("text").map(String::valueOf).orElse("");
            json = MqttPayloadJson.parseObject(text);
        }
        if (function.awaitsReply()) {
            Attributes payload = json.isEmpty() ? draft.payload() : Attributes.from(json);
            return InboundApplyResult.continueWith(
                    draft.withPayload(payload).withHeaders(headers).appendTrace(name(), "reply-keep"));
        }
        Map<String, Object> points = PayloadDisassembler.project(
                json, function.readFields(), function.readValueOptions(),
                function.scaleOp(), function.scaleOperand());
        if (skipUnpicked(function, points)) {
            return InboundApplyResult.drop();
        }
        return InboundApplyResult.continueWith(
                draft.withPayload(Attributes.from(points)).withHeaders(headers).appendTrace(name(), "disassemble"));
    }

    private static boolean skipUnpicked(FunctionDef function, Map<String, Object> points) {
        return function.readFields() != null
                && !function.readFields().isEmpty()
                && (points == null || points.isEmpty());
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
