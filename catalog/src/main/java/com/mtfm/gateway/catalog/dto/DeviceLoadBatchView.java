package com.mtfm.gateway.catalog.dto;

import java.util.List;

/**
 * 批量 load 结果。
 */
public record DeviceLoadBatchView(
        int requested,
        int loaded,
        int skipped,
        int failed,
        List<DeviceLoadItemView> items
) {
}
