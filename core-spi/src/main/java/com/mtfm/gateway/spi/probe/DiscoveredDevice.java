package com.mtfm.gateway.spi.probe;

import com.mtfm.gateway.spi.model.Attributes;

/**
 * 通道探针发现的一台子设备。
 *
 * @param deviceCode 业务编码（海康为 EhomeID）
 * @param name       显示名
 * @param address    端点寻址（海康为 deviceSerialNo=devIndex）
 * @param online     当前在线
 */
public record DiscoveredDevice(
        String deviceCode,
        String name,
        Attributes address,
        boolean online
) {
}
