package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mtfm.gateway.catalog.mybatis.JsonColumnTypeHandler;

import java.time.Instant;

/**
 * 共享通道实体：多设备复用同一物理/会话连接。
 *
 * <p>{@code connection} 存连接参数 JSON（host/port/username 等）；
 * 子设备寻址（slaveId、topic）在 {@link DeviceEndpointEntity#address}，不进本表。
 */
@TableName(value = "gw_channel", autoResultMap = true)
public class ChannelEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 通道业务编码，全局唯一，如 {@code gw-modbus-1}；运行时常用作 channelId。 */
    private String code;

    /**
     * 南向能力类型，如 {@code MODBUS} / {@code MQTT} / {@code HIKVISION_ENTRANCE}。
     * <p>须与能力注册中心 {@code CapabilityDescriptor.capabilityType} 一致。
     */
    private String capabilityType;

    /**
     * 连接参数 JSON，字段由能力 {@code connectionSchema} 定义。
     * <p>例 Modbus：{@code {"host":"192.168.1.10","port":502}}；
     * MQTT：{@code {"host":"broker.local","port":1883,"username":"u"}}。
     */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String connection;

    /** 是否启用；禁用后不再参与 load。默认 {@code true}。 */
    private Boolean enabled;

    /** 创建时间。 */
    private Instant createdAt;

    /** 最近更新时间。 */
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
