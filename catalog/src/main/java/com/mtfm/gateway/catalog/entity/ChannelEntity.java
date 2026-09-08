package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 共享通道实体：多设备复用同一物理/会话连接。
 *
 * <p>连接参数在 EAV {@code gw_channel_property}；子设备寻址在端点 EAV。
 *
 * <p>示例：{@code code="gw-modbus-1", capabilityType="MODBUS"}
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
