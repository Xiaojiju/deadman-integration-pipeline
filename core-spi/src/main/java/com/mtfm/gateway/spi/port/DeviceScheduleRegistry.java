package com.mtfm.gateway.spi.port;

import java.util.List;

/**
 * 设备定时下发登记口。load/unload 时按设备整体替换；实现不得为每设备建 Timer。
 */
public interface DeviceScheduleRegistry {

    /** 间隔下限（毫秒），低于此值的功能不登记。 */
    long MIN_INTERVAL_MS = 1000L;

    void replace(String deviceId, List<ScheduledFunction> jobs);

    void remove(String deviceId);

    record ScheduledFunction(String functionId, long intervalMs) {
        public ScheduledFunction {
            if (functionId == null || functionId.isBlank()) {
                throw new IllegalArgumentException("functionId 不能为空");
            }
            if (intervalMs < 1) {
                throw new IllegalArgumentException("intervalMs 必须为正");
            }
        }
    }
}
