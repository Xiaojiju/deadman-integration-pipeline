package com.mtfm.gateway.spi.model;

import java.util.Objects;

/**
 * 出站插件处理结果。禁止改 {@code channelHint}。
 *
 * <p>使用示例：
 * <pre>{@code
 * return OutboundApplyResult.continueWith(draft.withBody(response.withData(data)));
 * return OutboundApplyResult.reject("filter", "遥测被策略拦截");
 * return OutboundApplyResult.drop();
 * }</pre>
 */
public sealed interface OutboundApplyResult {

    /** 携带改写后的草稿继续插件链。 */
    record Continue(OutboundDraft draft) implements OutboundApplyResult {
        public Continue {
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** 业务拒绝，出站路径失败即停。 */
    record Reject(PluginReject reject) implements OutboundApplyResult {
        public Reject {
            Objects.requireNonNull(reject, "reject");
        }
    }

    /** 静默丢弃，不再发布。 */
    record Drop() implements OutboundApplyResult {
    }

    /** 继续处理并携带改写后的草稿。 */
    static Continue continueWith(OutboundDraft draft) {
        return new Continue(draft);
    }

    /** 以 {@link PluginReject} 拒绝。 */
    static Reject reject(PluginReject reject) {
        return new Reject(reject);
    }

    /** 以插件名与消息拒绝。 */
    static Reject reject(String source, String message) {
        return new Reject(PluginReject.of(source, message));
    }

    /** 静默丢弃。 */
    static Drop drop() {
        return new Drop();
    }
}
