package com.mtfm.gateway.spi.model;

import java.util.Objects;

/**
 * 插件业务拒绝。入站 Reject 触发失败即停，不贴胶、不进 Execute。
 *
 * @param source  插件名
 * @param message 拒绝原因
 */
public record PluginReject(String source, String message) {

    public PluginReject {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(message, "message");
    }

    public static PluginReject of(String source, String message) {
        return new PluginReject(source, message);
    }

    /** 转换为 {@link Failure}，用于 Correlate 嵌进回执。 */
    public Failure toFailure() {
        return Failure.of(FailureCodes.PLUGIN_REJECT, message, source, false);
    }
}
