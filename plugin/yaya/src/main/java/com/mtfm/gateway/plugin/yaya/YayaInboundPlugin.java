package com.mtfm.gateway.plugin.yaya;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.plugin.InboundPlugin;

import java.util.Set;

/**
 * Yaya 入站插件：按设备 ID 前缀匹配，order 最低优先执行。
 *
 * <p>将 payload 中的 {@code cmd} 字段归一化为 {@code action}，兼容旧版 Yaya 协议。
 * 只依赖信封 SPI，不 import 任何 capability 模块。
 *
 * <pre>{@code
 * // deviceId = "yaya-sensor-01", payload = {cmd: "on"}
 * // → payload 变为 {cmd: "on", action: "on"}
 * }</pre>
 */
public final class YayaInboundPlugin implements InboundPlugin {

    private final Set<String> prefixes;

    public YayaInboundPlugin() {
        this(Set.of("yaya-", "yy-"));
    }

    public YayaInboundPlugin(Set<String> prefixes) {
        this.prefixes = prefixes;
    }

    @Override
    public String name() {
        return "yaya";
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean support(EnvelopeDraft draft) {
        String deviceId = draft.deviceId();
        return prefixes.stream().anyMatch(deviceId::startsWith);
    }

    @Override
    public InboundApplyResult apply(EnvelopeDraft draft) {
        if (draft.payload().get("cmd").isPresent() && draft.payload().get("action").isEmpty()) {
            Object cmd = draft.payload().get("cmd").orElse(null);
            return InboundApplyResult.continueWith(
                    draft.withPayload(draft.payload().with("action", cmd)).appendTrace(name(), "cmd->action"));
        }
        return InboundApplyResult.continueWith(draft.appendTrace(name(), "pass"));
    }
}
