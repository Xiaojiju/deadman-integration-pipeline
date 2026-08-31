package com.mtfm.gateway.spi.plugin;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.InboundApplyResult;

/**
 * 入站插件扩展点。只改未贴胶草稿，不得持有 Driver / Publisher，不得推进阶段。
 *
 * <p>核心按 {@link #order()} 升序调用；{@link #support(EnvelopeDraft)} 为 {@code true} 才进入链。
 * {@link #apply(EnvelopeDraft)} 返回 {@link InboundApplyResult} 控制继续、拒绝或丢弃。
 *
 * <p>使用示例：
 * <pre>{@code
 * public final class AuthInboundPlugin implements InboundPlugin {
 *     public String name() { return "auth"; }
 *     public int order() { return 100; }
 *     public boolean support(EnvelopeDraft draft) {
 *         return draft.kind() == EnvelopeKind.COMMAND;
 *     }
 *     public InboundApplyResult apply(EnvelopeDraft draft) {
 *         if (!allowed(draft.deviceId())) {
 *             return InboundApplyResult.reject(name(), "设备未授权");
 *         }
 *         return InboundApplyResult.continueWith(draft.appendTrace(name(), "ok"));
 *     }
 * }
 * }</pre>
 */
public interface InboundPlugin {

    /** 插件唯一名称，用于轨迹与失败来源。 */
    String name();

    /** 链内排序，数值越小越先执行。 */
    int order();

    /** 是否处理该草稿。 */
    boolean support(EnvelopeDraft draft);

    /** 处理草稿并返回继续/拒绝/丢弃结果。 */
    InboundApplyResult apply(EnvelopeDraft draft);
}
