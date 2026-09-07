package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 设备功能定时下发覆盖。空字段表示继承产品 {@code schedule_enabled} / {@code schedule_interval_ms}。
 */
@TableName("gw_device_function_schedule")
public class DeviceFunctionScheduleEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属设备主键，对应 {@link DeviceEntity#id}。 */
    private String deviceId;

    /** 目标功能 ID。 */
    private String functionId;

    /** 是否启用定时下发；{@code null} 表示继承产品功能默认值。 */
    private Boolean enabled;

    /** 下发间隔毫秒；{@code null} 表示继承产品功能默认值。 */
    private Long intervalMs;

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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(Long intervalMs) {
        this.intervalMs = intervalMs;
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
