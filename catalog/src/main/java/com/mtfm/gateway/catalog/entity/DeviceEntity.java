package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mtfm.gateway.catalog.mybatis.JsonColumnTypeHandler;

import java.time.Instant;

/**
 * 设备实例实体。
 *
 * <p>
 * {@code deviceCode} 即流水线 {@code deviceId}（业务设备编码，不是网关编码）；
 * {@code optionOverrides} 只存相对产品功能的差异参数。
 */
@TableName(value = "gw_device", autoResultMap = true)
public class DeviceEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /**
     * 设备业务编码，全局唯一；流水线路由键 {@code deviceId}。
     * <p>
     * 例：{@code A}、{@code pump-01}。
     */
    private String deviceCode;

    /** 引用的产品主键，对应 {@link ProductEntity#id}。 */
    private String productId;

    /** 设备显示名称，可空。 */
    private String name;

    /**
     * 功能参数覆盖 JSON：相对产品 {@code optionSchema} 的差异节点。
     * <p>
     * 可按 functionId 分键，例：{@code {"fn.read":{"offset":10}}}；
     * 也可是扁平覆盖（无 fn. 前缀时整表合并）。
     */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String optionOverrides;

    /** 是否启用；禁用后不参与全量 reload。默认 {@code true}。 */
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

    public String getOptionOverrides() {
        return optionOverrides;
    }

    public void setOptionOverrides(String optionOverrides) {
        this.optionOverrides = optionOverrides;
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
