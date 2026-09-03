package com.mtfm.gateway.catalog.dto;

/**
 * 设备功能定时下发视图：生效值 + 产品默认 + 是否覆盖。
 */
public record DeviceFunctionScheduleView(
        String functionId,
        boolean enabled,
        Long intervalMs,
        boolean productEnabled,
        Long productIntervalMs,
        boolean overridden,
        Boolean overrideEnabled,
        Long overrideIntervalMs
) {
}
