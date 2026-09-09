package com.mtfm.gateway.spi.presence;

/**
 * 一台设备的在线提示。catalog 只更新已存在的设备。
 */
public record DeviceOnlineHint(String deviceCode, boolean online) {
}
