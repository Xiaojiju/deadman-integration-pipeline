package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 产品实体：定义同质设备的共享功能模板与协议映射。
 *
 * <p>多台设备引用同一产品后，功能 schema 从产品继承，设备侧只存差异覆盖。
 */
@TableName("gw_product")
public class ProductEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 产品业务编码，全局唯一，如 {@code pump-v1}。 */
    private String code;

    /** 产品显示名称。 */
    private String name;

    /** 产品说明，可空。 */
    private String description;

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
