package com.mtfm.gateway.spi.model;

/**
 * 信封方向。
 */
public enum Direction {
    /** 南向入站（协议 → 核心）。 */
    INBOUND,
    /** 北向出站（核心 → 云/上层）。 */
    OUTBOUND
}
