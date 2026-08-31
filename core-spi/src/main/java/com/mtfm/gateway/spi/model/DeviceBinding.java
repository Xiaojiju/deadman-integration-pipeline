package com.mtfm.gateway.spi.model;

/**
 * 设备与南向能力的绑定。同一 {@code deviceId} 只能对应一种 {@code capabilityType}。
 *
 * @param deviceId       设备 ID
 * @param capabilityType 南向能力类型
 */
public record DeviceBinding(String deviceId, String capabilityType) {

    public DeviceBinding {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (capabilityType == null || capabilityType.isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
    }
}
