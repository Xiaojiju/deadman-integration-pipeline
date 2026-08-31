package com.mtfm.gateway.plugin.struct;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import com.mtfm.gateway.spi.plugin.InboundPlugin;

/**
 * Struct 入站插件：为 STRUCT 类型 payload 填充默认 fields 空 Map。
 *
 * <p>当 payload 含 {@code struct} 字段或 {@code valueAccessType=STRUCT} 时激活，
 * order=100，在 Yaya 等前缀插件之后执行。只依赖信封 SPI。
 *
 * <pre>{@code
 * // payload = {struct: "temperature", valueAccessType: "STRUCT"}
 * // → 若 fields 缺失，自动补 fields={}
 * }</pre>
 */
public final class StructInboundPlugin implements InboundPlugin {

    @Override
    public String name() {
        return "struct";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean support(EnvelopeDraft draft) {
        return draft.payload().get("struct").isPresent()
                || "STRUCT".equalsIgnoreCase(draft.payload().get("valueAccessType").map(String::valueOf).orElse(""));
    }

    @Override
    public InboundApplyResult apply(EnvelopeDraft draft) {
        Attributes payload = draft.payload();
        if (payload.get("fields").isEmpty()) {
            payload = payload.with("fields", java.util.Map.of());
        }
        return InboundApplyResult.continueWith(draft.withPayload(payload).appendTrace(name(), "default-fields"));
    }
}
