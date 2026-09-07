package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 动作组实体：集群（CLUSTER）或场景（SCENE）的元数据容器。
 *
 * <p>成员与触发器分别见 {@link ActionMemberEntity}、{@link SceneTriggerEntity}。
 */
@TableName("gw_action_group")
public class ActionGroupEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 业务编码，全局唯一，如 {@code night-mode}。 */
    private String code;

    /** 显示名称。 */
    private String name;

    /** 说明，可空。 */
    private String description;

    /** 种类：{@code CLUSTER}（手动批量）或 {@code SCENE}（监听/定时触发）。 */
    private String kind;

    /** 是否启用；禁用后调度器与监听不再触发。 */
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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
