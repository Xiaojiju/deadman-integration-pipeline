package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备级字段覆盖：按功能 path 覆盖产品默认 caller 字段值。
 */
@TableName("gw_device_field_override")
public class DeviceFieldOverrideEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属设备主键，对应 {@link DeviceEntity#id}。 */
    private String deviceId;

    /** 目标功能 ID。 */
    private String functionId;

    /** 字段 path，如 {@code offset} 或嵌套 {@code params.area}。 */
    private String fieldPath;

    /** 覆盖值（字符串存储，运行时按字段类型转换）。 */
    private String fieldValue;

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

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}
