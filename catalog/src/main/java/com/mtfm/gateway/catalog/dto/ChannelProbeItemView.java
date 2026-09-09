package com.mtfm.gateway.catalog.dto;

/**
 * 探针单条结果。
 *
 * @param action created / serialUpdated / unchanged
 */
public record ChannelProbeItemView(
        String deviceCode,
        String name,
        String serial,
        boolean online,
        String action
) {
}
