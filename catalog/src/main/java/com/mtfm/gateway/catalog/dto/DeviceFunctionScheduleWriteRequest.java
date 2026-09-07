package com.mtfm.gateway.catalog.dto;

import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import jakarta.validation.constraints.Min;

/**
 * 设备功能定时下发覆盖。两字段皆空表示删除覆盖、继承产品。
 */
public record DeviceFunctionScheduleWriteRequest(
        Boolean enabled,
        @Min(value = DeviceScheduleRegistry.MIN_INTERVAL_MS, message = "intervalMs 不能小于 1000")
        Long intervalMs) {
}
