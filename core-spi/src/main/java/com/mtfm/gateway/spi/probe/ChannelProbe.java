package com.mtfm.gateway.spi.probe;

/**
 * 通道探针：主动扫描通道下的子设备。由能力模块实现，catalog 只依赖本接口。
 */
public interface ChannelProbe {

    boolean supports(String capabilityType);

    ChannelProbeResult scan(ChannelProbeRequest request);
}
