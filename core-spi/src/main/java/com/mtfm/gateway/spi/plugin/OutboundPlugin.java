package com.mtfm.gateway.spi.plugin;

import com.mtfm.gateway.spi.model.OutboundApplyResult;
import com.mtfm.gateway.spi.model.OutboundDraft;

/**
 * 出站插件扩展点。只改 body / headers，禁止改 {@code channelHint}，禁止调用 Publisher。
 *
 * <p>核心在 Egress 阶段按 {@link #order()} 升序调用；{@link #support(OutboundDraft)} 为
 * {@code true} 才进入链。
 *
 * <p>使用示例：
 * <pre>{@code
 * public final class TraceOutboundPlugin implements OutboundPlugin {
 *     public String name() { return "trace"; }
 *     public int order() { return 50; }
 *     public boolean support(OutboundDraft draft) { return true; }
 *     public OutboundApplyResult apply(OutboundDraft draft) {
 *         return OutboundApplyResult.continueWith(
 *                 draft.withHeaders(draft.headers().with("x-plugin", name())));
 *     }
 * }
 * }</pre>
 */
public interface OutboundPlugin {

    /** 插件唯一名称。 */
    String name();

    /** 链内排序，数值越小越先执行。 */
    int order();

    /** 是否处理该出站草稿。 */
    boolean support(OutboundDraft draft);

    /** 处理草稿并返回继续/拒绝/丢弃结果。 */
    OutboundApplyResult apply(OutboundDraft draft);
}
