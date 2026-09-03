package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.DeviceFunctionScheduleEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSchedulesTest {

    @Test
    void usesProductDefaultsWhenNoOverride() {
        ProductFunctionEntity function = function("fn.poll", true, 5000L);
        List<DeviceScheduleRegistry.ScheduledFunction> jobs = CatalogSchedules.resolve(
                List.of(function), Map.of(), DeviceScheduleRegistry.MIN_INTERVAL_MS);
        assertEquals(1, jobs.size());
        assertEquals("fn.poll", jobs.getFirst().functionId());
        assertEquals(5000L, jobs.getFirst().intervalMs());
    }

    @Test
    void deviceDisableWinsOverProduct() {
        ProductFunctionEntity function = function("fn.poll", true, 5000L);
        DeviceFunctionScheduleEntity override = override(false, null);
        List<DeviceScheduleRegistry.ScheduledFunction> jobs = CatalogSchedules.resolve(
                List.of(function), Map.of("fn.poll", override), DeviceScheduleRegistry.MIN_INTERVAL_MS);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void deviceIntervalOverridesProduct() {
        ProductFunctionEntity function = function("fn.poll", true, 5000L);
        DeviceFunctionScheduleEntity override = override(null, 2000L);
        List<DeviceScheduleRegistry.ScheduledFunction> jobs = CatalogSchedules.resolve(
                List.of(function), Map.of("fn.poll", override), DeviceScheduleRegistry.MIN_INTERVAL_MS);
        assertEquals(2000L, jobs.getFirst().intervalMs());
    }

    @Test
    void skipsBelowMinimumInterval() {
        ProductFunctionEntity function = function("fn.poll", true, 20L);
        List<DeviceScheduleRegistry.ScheduledFunction> jobs = CatalogSchedules.resolve(
                List.of(function), Map.of(), DeviceScheduleRegistry.MIN_INTERVAL_MS);
        assertTrue(jobs.isEmpty());
    }

    private static ProductFunctionEntity function(String functionId, boolean enabled, Long intervalMs) {
        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setFunctionId(functionId);
        entity.setScheduleEnabled(enabled);
        entity.setScheduleIntervalMs(intervalMs);
        return entity;
    }

    private static DeviceFunctionScheduleEntity override(Boolean enabled, Long intervalMs) {
        DeviceFunctionScheduleEntity entity = new DeviceFunctionScheduleEntity();
        entity.setFunctionId("fn.poll");
        entity.setEnabled(enabled);
        entity.setIntervalMs(intervalMs);
        return entity;
    }
}
