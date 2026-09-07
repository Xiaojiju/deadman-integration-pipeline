package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 场景触发器：定义 SCENE 动作组的监听或定时条件。
 *
 * <p>{@code mode=LISTEN} 时填写 listen* 字段；{@code mode=TIMER} 时填写 timer* 字段。
 */
@TableName("gw_scene_trigger")
public class SceneTriggerEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属场景组主键，对应 {@link ActionGroupEntity#id}。 */
    private String groupId;

    /** 触发模式：{@code LISTEN}（设备事件）或 {@code TIMER}（定时）。 */
    private String mode;

    /** 监听模式：被监听设备编码。 */
    private String listenDeviceCode;

    /** 监听模式：被监听功能 ID。 */
    private String listenFunctionId;

    /** 监听模式：回包匹配条件 JSON（字段 path → 期望值）。 */
    private String listenMatchJson;

    /** 定时模式：{@code ONCE}（单次）或 {@code CRON}（周期）。 */
    private String timerKind;

    /** 定时模式（ONCE）：ISO-8601 触发时刻。 */
    private String timerAt;

    /** 定时模式（CRON）：Cron 表达式。 */
    private String cronExpr;

    /** 定时模式：时区，如 {@code Asia/Shanghai}。 */
    private String timezone;

    /** 是否启用此触发器。 */
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getListenDeviceCode() {
        return listenDeviceCode;
    }

    public void setListenDeviceCode(String listenDeviceCode) {
        this.listenDeviceCode = listenDeviceCode;
    }

    public String getListenFunctionId() {
        return listenFunctionId;
    }

    public void setListenFunctionId(String listenFunctionId) {
        this.listenFunctionId = listenFunctionId;
    }

    public String getListenMatchJson() {
        return listenMatchJson;
    }

    public void setListenMatchJson(String listenMatchJson) {
        this.listenMatchJson = listenMatchJson;
    }

    public String getTimerKind() {
        return timerKind;
    }

    public void setTimerKind(String timerKind) {
        this.timerKind = timerKind;
    }

    public String getTimerAt() {
        return timerAt;
    }

    public void setTimerAt(String timerAt) {
        this.timerAt = timerAt;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
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
