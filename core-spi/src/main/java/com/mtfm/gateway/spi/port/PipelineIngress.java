package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.RawInbound;

/**
 * 入站口。协议回调必须交回本口，不得自建业务队列推进阶段。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 已解码草稿
 * pipeline.accept(EnvelopeDraft.builder().deviceId("d1").kind(EnvelopeKind.COMMAND).build());
 *
 * // 原始字节，核心按 capabilityType 选 Driver.decode
 * pipeline.acceptRaw(RawInbound.builder()
 *         .capabilityType("modbus-tcp").bytes(frame).build());
 * }</pre>
 */
public interface PipelineIngress {

    /**
     * 已解码草稿入站。
     *
     * @param draft 未贴胶入站草稿
     * @return 是否入队成功
     */
    boolean accept(EnvelopeDraft draft);

    /**
     * 原始入站，由核心按 capabilityType 选 {@link com.mtfm.gateway.spi.capability.Driver#decode}。
     *
     * @param raw 原始字节/文本
     * @return 是否入队成功
     */
    boolean acceptRaw(RawInbound raw);
}
