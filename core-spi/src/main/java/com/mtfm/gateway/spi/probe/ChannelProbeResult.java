package com.mtfm.gateway.spi.probe;

import java.util.List;

/**
 * 通道探针扫描结果。
 */
public record ChannelProbeResult(List<DiscoveredDevice> devices) {

    public ChannelProbeResult {
        devices = devices == null ? List.of() : List.copyOf(devices);
    }
}
