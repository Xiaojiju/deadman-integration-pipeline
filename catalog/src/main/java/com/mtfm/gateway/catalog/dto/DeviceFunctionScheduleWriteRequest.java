package com.mtfm.gateway.catalog.dto;

/**
 * 设备功能定时下发覆盖。两字段皆空表示删除覆盖、继承产品。
 */
public record DeviceFunctionScheduleWriteRequest(Boolean enabled, Long intervalMs) {
}
