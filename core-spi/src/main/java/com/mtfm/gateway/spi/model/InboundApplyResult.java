package com.mtfm.gateway.spi.model;

import java.util.Objects;

/**
 * 入站插件处理结果。命令路径 {@link Reject} 失败即停：不贴胶、不进 Execute。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 继续处理
 * return InboundApplyResult.continueWith(draft.withFunctionId("fn.write"));
 *
 * // 业务拒绝
 * return InboundApplyResult.reject("auth", "无写权限");
 *
 * // 静默丢弃（遥测路径）
 * return InboundApplyResult.drop();
 * }</pre>
 */
public sealed interface InboundApplyResult {

    /** 携带改写后的草稿继续插件链。 */
    record Continue(EnvelopeDraft draft) implements InboundApplyResult {
        public Continue {
            Objects.requireNonNull(draft, "draft");
        }
    }

    /** 业务拒绝，命令路径失败即停。 */
    record Reject(PluginReject reject) implements InboundApplyResult {
        public Reject {
            Objects.requireNonNull(reject, "reject");
        }
    }

    /** 静默丢弃，不再进入后续阶段。 */
    record Drop() implements InboundApplyResult {
    }

    /** 继续处理并携带改写后的草稿。 */
    static Continue continueWith(EnvelopeDraft draft) {
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
