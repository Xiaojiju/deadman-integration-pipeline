package com.mtfm.gateway.spi.capability;

import com.mtfm.gateway.spi.exception.DecodeException;
import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.RawInbound;

/**
 * 南向协议驱动：只负责将原始入站解码为未贴胶草稿。
 *
 * <p>禁止在 {@link #decode(RawInbound)} 内调用流水线下一阶段或 Publisher。
 * 每个实现对应一种 {@link #capabilityType()}。
 *
 * <p>使用示例：
 * <pre>{@code
 * public final class ModbusDriver implements Driver {
 *     public String capabilityType() { return "modbus-tcp"; }
 *     public EnvelopeDraft decode(RawInbound raw) throws DecodeException {
 *         ModbusFrame frame = ModbusFrame.parse(raw.bytes());
 *         return EnvelopeDraft.builder()
 *                 .deviceId(frame.deviceId())
 *                 .kind(EnvelopeKind.TELEMETRY)
 *                 .payload(Map.of("register", frame.value()))
 *                 .build();
 *     }
 * }
 * }</pre>
 */
public interface Driver {

    /** 本驱动对应的能力类型标识。 */
    String capabilityType();

    /**
     * 将原始入站解码为草稿。
     *
     * @param raw 原始字节/文本，不含连接句柄
     * @return 未贴胶入站草稿
     * @throws DecodeException 解码失败
     */
    EnvelopeDraft decode(RawInbound raw) throws DecodeException;
}
