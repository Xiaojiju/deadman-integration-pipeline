package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mtfm.gateway.catalog.mybatis.JsonColumnTypeHandler;

import java.time.Instant;

/**
 * 设备端点实体：把设备绑到一条共享通道，并带上地址片。
 *
 * <p>Channel 承载共享连接（host/port）；Address 承载本设备寻址（slaveId / topic / doorNo）。
 * 同一 Channel 可挂多个 Endpoint（例如一台 Modbus 网关下多个从站）。
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

    /**
     * 地址片 JSON，字段由能力 {@code addressSchema} 定义。
     * <p>例 Modbus：{@code {"slaveId":1}}；MQTT：{@code {"topic":"dev/A/cmd"}}；
     * 海康：{@code {"doorNo":1}}。
     */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String address;

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
