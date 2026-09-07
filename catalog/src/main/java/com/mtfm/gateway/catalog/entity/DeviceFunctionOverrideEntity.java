package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备功能覆盖实体类
 * 
 * @author moyang
 * @version 1.0
 * @since 1.0
 * @date 2026-09-01
 * @description 设备功能覆盖实体类
 * 
 * @see com.mtfm.gateway.catalog.entity.DeviceFunctionOverrideEntity
 * @see com.mtfm.gateway.catalog.mapper.DeviceFunctionOverrideMapper
 */
@TableName("gw_device_function_override")
public class DeviceFunctionOverrideEntity extends AbstractPropertyEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;
    /**
     * 设备ID
     */
    private String deviceId;
    /**
     * 功能ID
     */
    private String functionId;

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
}
