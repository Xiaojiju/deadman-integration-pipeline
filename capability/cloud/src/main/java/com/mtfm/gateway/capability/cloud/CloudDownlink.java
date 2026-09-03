package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.port.PipelineIngress;

import java.util.Map;

/**
 * 遗留云下行适配器：直接 {@code accept(COMMAND)} 会丢掉 catalog 装配的 topic hints。
 *
 * <p>北向 MQTT 命令请使用 {@link NorthboundMqttIngress} + {@link NorthboundCommandPort}。
 */
@Deprecated
public final class CloudDownlink {

    private final PipelineIngress ingress;

    public CloudDownlink(PipelineIngress ingress) {
        this.ingress = ingress;
    }

    public boolean acceptCommand(String deviceId, String functionId, Map<String, ?> payload) {
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .kind(EnvelopeKind.COMMAND)
                .deviceId(deviceId)
                .functionId(functionId)
                .payload(payload)
                .build();
        return ingress.accept(draft);
    }
}
