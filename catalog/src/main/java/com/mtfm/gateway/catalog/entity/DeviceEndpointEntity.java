package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 设备端点实体：把设备绑到一条共享通道。
 *
 * <p>寻址片在 EAV {@code gw_endpoint_property}，不进本表。
 */
@TableName(value = "gw_device_endpoint", autoResultMap = true)
public class DeviceEndpointEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属设备主键，对应 {@link DeviceEntity#id}（不是 deviceCode）。 */
    private String deviceId;

    /** 所属通道主键，对应 {@link ChannelEntity#id}。 */
    private String channelId;

    /** 创建时间。 */
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
