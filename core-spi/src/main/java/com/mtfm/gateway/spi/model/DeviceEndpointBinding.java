package com.mtfm.gateway.spi.model;

/**
 * 设备端点绑定：通道 + 地址片。从站/子设备 key 属于 address，不属于 Option。
 *
 * @param deviceId        设备 ID
 * @param channelId       通道业务编码
 * @param capabilityType  能力类型
 * @param connection      通道连接参数
 * @param address         设备地址片参数
 * @param channelEnabled  所属通道是否启用
 */
public record DeviceEndpointBinding(
        String deviceId,
        String channelId,
        String capabilityType,
        Attributes connection,
        Attributes address,
        boolean channelEnabled
) {

    public DeviceEndpointBinding {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        if (capabilityType == null || capabilityType.isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
        connection = connection == null ? Attributes.empty() : connection;
        address = address == null ? Attributes.empty() : address;
    }

    public DeviceEndpointBinding(
            String deviceId,
            String channelId,
            String capabilityType,
            Attributes connection,
            Attributes address) {
        this(deviceId, channelId, capabilityType, connection, address, true);
    }
}
