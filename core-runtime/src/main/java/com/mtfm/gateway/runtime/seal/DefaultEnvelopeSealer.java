package com.mtfm.gateway.runtime.seal;

import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.OutboundDraft;
import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.port.EnvelopeSealer;

import java.util.UUID;

/**
 * 默认信封贴胶器：为草稿分配全局唯一 ID 后产出不可变 {@link Envelope} / {@link OutboundMessage}。
 *
 * <p>入站贴胶生成 {@code envelopeId}；出站贴胶在 {@code messageId} 为空时自动生成 UUID。
 * 贴胶后的对象不可变，后续阶段只读传递。
 *
 * <p>使用示例：
 * <pre>{@code
 * DefaultEnvelopeSealer sealer = new DefaultEnvelopeSealer();
 * Envelope sealed = sealer.seal(draft.appendTrace("seal", "inbound"));
 * OutboundMessage outbound = sealer.seal(OutboundDraft.builder()
 *         .channelHint("CLOUD").body(response).build());
 * }</pre>
 */
public final class DefaultEnvelopeSealer implements EnvelopeSealer {

    @Override
    public Envelope seal(EnvelopeDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft 不能为空");
        }
        return new Envelope(
                UUID.randomUUID().toString(),
                draft.requestId(),
                draft.direction(),
                draft.kind(),
                draft.deviceId(),
                draft.functionId(),
                draft.capabilityType(),
                draft.payload(),
                draft.headers(),
                draft.trace(),
                draft.error(),
                draft.createdAt(),
                draft.deadlineAt()
        );
    }

    @Override
    public OutboundMessage seal(OutboundDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft 不能为空");
        }
        String messageId = draft.messageId() == null || draft.messageId().isBlank()
                ? UUID.randomUUID().toString()
                : draft.messageId();
        return new OutboundMessage(
                messageId,
                draft.channelHint(),
                draft.body(),
                draft.headers(),
                draft.priority(),
                draft.attempt()
        );
    }
}
