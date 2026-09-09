package com.mtfm.gateway.catalog.dto;

import java.util.List;

/**
 * 通道探针汇总。
 */
public record ChannelProbeView(
        String channelId,
        String channelCode,
        String productId,
        int discovered,
        int created,
        int serialUpdated,
        int unchanged,
        List<ChannelProbeItemView> items
) {
}
