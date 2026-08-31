package com.mtfm.gateway.spi.model;

/**
 * 信封种类。
 *
 * <ul>
 *   <li>{@link #COMMAND} — 走 Execute 阶段</li>
 *   <li>{@link #TELEMETRY} — 跳过 Execute，直接 Correlate/Egress</li>
 *   <li>{@link #RESPONSE} — 由 Correlate 组装的命令回执</li>
 * </ul>
 */
public enum EnvelopeKind {
    /** 南向功能命令。 */
    COMMAND,
    /** 遥测/事件上报。 */
    TELEMETRY,
    /** 命令执行回执。 */
    RESPONSE
}
