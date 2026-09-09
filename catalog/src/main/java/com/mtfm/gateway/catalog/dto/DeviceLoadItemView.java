package com.mtfm.gateway.catalog.dto;

/**
 * 批量 load 单条。status：loaded / skipped / failed。
 */
public record DeviceLoadItemView(
        String deviceCode,
        String status,
        String error
) {
}
