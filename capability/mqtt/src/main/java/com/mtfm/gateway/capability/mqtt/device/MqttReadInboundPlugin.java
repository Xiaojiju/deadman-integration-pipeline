package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.FunctionDef;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.payload.PayloadDisassembler;
import com.mtfm.gateway.spi.plugin.InboundPlugin;

import java.util.Map;
import java.util.Optional;

/**
 * MQTT READ 入站：按产品 readFields 从 JSON 载荷提取遥测点。
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
        Map<String, Object> json = draft.payload().values();
        if (json.isEmpty() || json.containsKey("text")) {
            String text = draft.payload().get("text").map(String::valueOf).orElse("");
            json = MqttPayloadJson.parseObject(text);
        }
        Map<String, Object> points = PayloadDisassembler.disassemble(json, def.get().readFields());
        Attributes payload = Attributes.from(points);
        return InboundApplyResult.continueWith(
                draft.withPayload(payload).appendTrace(name(), "disassemble"));
    }
}
