package com.mtfm.gateway.spi.presence;

import com.mtfm.gateway.spi.probe.ChannelProbeRequest;

import java.util.List;

/**
 * 能力原生在线轮询（海康 NATIVE：再打一遍 deviceList）。
 */
public interface PresencePoller {

    boolean supports(String capabilityType);

    List<DeviceOnlineHint> poll(ChannelProbeRequest request);
}
