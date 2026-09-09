package com.mtfm.gateway.spi.probe;

import java.util.Optional;

/**
 * 按能力类型查找通道探针。
 */
public interface ChannelProbeRegistry {

    Optional<ChannelProbe> find(String capabilityType);
}
