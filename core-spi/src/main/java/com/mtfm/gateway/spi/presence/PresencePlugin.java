package com.mtfm.gateway.spi.presence;

import java.util.List;

/**
 * 特殊设备/网关批量在线上报的翻译插件。不复用入站 {@code InboundPlugin}。
 */
public interface PresencePlugin {

    String name();

    boolean support(PresenceSignal signal);

    List<DeviceOnlineHint> interpret(PresenceSignal signal);
}
