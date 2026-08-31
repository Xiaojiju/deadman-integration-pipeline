package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.model.Envelope;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.OutboundDraft;
import com.mtfm.gateway.spi.model.OutboundMessage;

/**
 * 贴胶器。由调度核心持有；插件不得调用。
 *
 * <p>为草稿分配全局唯一 ID 后产出不可变 {@link com.mtfm.gateway.spi.model.Envelope} /
 * {@link com.mtfm.gateway.spi.model.OutboundMessage}。
 *
 * <p>使用示例：
 * <pre>{@code
 * Envelope inbound = sealer.seal(envelopeDraft);
 * OutboundMessage outbound = sealer.seal(outboundDraft);
 * }</pre>
 */
public interface EnvelopeSealer {

    /** 入站草稿贴胶，生成 {@link com.mtfm.gateway.spi.model.Envelope}。 */
    Envelope seal(EnvelopeDraft draft);

    /** 出站草稿贴胶，生成 {@link com.mtfm.gateway.spi.model.OutboundMessage}。 */
    OutboundMessage seal(OutboundDraft draft);
}
