package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 动作组成员：组内一条待执行的设备功能指令。
 */
@TableName("gw_action_member")
public class ActionMemberEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属动作组主键，对应 {@link ActionGroupEntity#id}。 */
    private String groupId;

    /** 目标设备业务编码（流水线路由键）。 */
    private String deviceCode;

    /** 目标功能 ID，如 {@code fn.write}。 */
    private String functionId;

    /** 调用参数 JSON，结构与功能 caller schema 对齐。 */
    private String argumentsJson;

    /** 组内执行顺序，数值越小越靠前。 */
    private Integer sortIndex;

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

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
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
