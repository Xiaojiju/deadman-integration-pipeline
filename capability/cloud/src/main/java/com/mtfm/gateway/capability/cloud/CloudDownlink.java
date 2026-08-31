package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.port.PipelineIngress;

import java.util.Map;

/**
 * 云下行适配器：将云端命令解码后交给 {@link PipelineIngress#accept}。
 *
 * <p>北向能力不实现南向 {@link com.mtfm.gateway.spi.capability.FunctionExecutor}。
 */
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
