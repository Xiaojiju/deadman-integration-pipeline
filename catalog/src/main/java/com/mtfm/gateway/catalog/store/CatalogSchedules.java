package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 产品定时配置与设备覆盖合并为可登记任务。
 */
final class CatalogSchedules {

    private CatalogSchedules() {
    }

    static List<DeviceScheduleRegistry.ScheduledFunction> resolve(
            List<ProductFunctionEntity> functions,
            Map<String, DeviceFunctionScheduleEntity> overrides,
            long minIntervalMs) {
        if (functions == null || functions.isEmpty()) {
            return List.of();
        }
        Map<String, DeviceFunctionScheduleEntity> byFunction = overrides == null ? Map.of() : overrides;
        List<DeviceScheduleRegistry.ScheduledFunction> jobs = new ArrayList<>();
        for (ProductFunctionEntity function : functions) {
            DeviceFunctionScheduleEntity override = byFunction.get(function.getFunctionId());
            boolean enabled = override != null && override.getEnabled() != null
                    ? Boolean.TRUE.equals(override.getEnabled())
                    : Boolean.TRUE.equals(function.getScheduleEnabled());
            if (!enabled) {
                continue;
            }
            Long interval = override != null && override.getIntervalMs() != null
                    ? override.getIntervalMs()
                    : function.getScheduleIntervalMs();
            if (interval == null || interval < minIntervalMs) {
                continue;
            }
            jobs.add(new DeviceScheduleRegistry.ScheduledFunction(function.getFunctionId(), interval));
        }
        return List.copyOf(jobs);
    }
}
