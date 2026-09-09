package com.mtfm.gateway.spi.presence;

import java.util.List;

/**
 * 把在线提示写入 catalog。能力/轮询只翻译，不直接改库。
 */
public interface DevicePresencePort {

    void apply(List<DeviceOnlineHint> hints);
}
