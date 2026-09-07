package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("gw_scene_trigger")
public class SceneTriggerEntity {

    @TableId
    private String id;
    private String groupId;
    private String mode;
    private String listenDeviceCode;
    private String listenFunctionId;
    private String listenMatchJson;
    private String timerKind;
    private String timerAt;
    private String cronExpr;
    private String timezone;
    private Boolean enabled;
    private Instant createdAt;
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
