package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 设备实例实体。
 *
 * <p>{@code deviceCode} 即流水线 {@code deviceId}。功能覆盖在 EAV，不进本表。
 *
 * <p>示例：{@code deviceCode="pump-01", productId="...", enabled=true}
 */
@TableName(value = "gw_device", autoResultMap = true)
public class DeviceEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /**
     * 设备业务编码，全局唯一；流水线路由键 {@code deviceId}。
     */
    private String deviceCode;

    /** 引用的产品主键，对应 {@link ProductEntity#id}。 */
    private String productId;

    /** 设备显示名称，可空。 */
    private String name;

    /** 是否启用；禁用后不参与全量 reload。默认 {@code true}。 */
    private Boolean enabled;

    /** 最近一次探针/在线监听得到的在线状态；空表示未知。 */
    private Boolean online;

    /** 在线状态最近更新时间。 */
    private Instant onlineUpdatedAt;

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

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Instant getOnlineUpdatedAt() {
        return onlineUpdatedAt;
    }

    public void setOnlineUpdatedAt(Instant onlineUpdatedAt) {
        this.onlineUpdatedAt = onlineUpdatedAt;
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
