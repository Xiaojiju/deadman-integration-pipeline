package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备主题覆盖实体类
 * 
 * @author moyang
 * @version 1.0
 * @since 1.0
 * @date 2026-09-01
 * @description 设备主题覆盖实体类
 * 
 * @see com.mtfm.gateway.catalog.entity.DeviceTopicOverrideEntity
 * @see com.mtfm.gateway.catalog.mapper.DeviceTopicOverrideMapper
 */
@TableName("gw_device_topic_override")
public class DeviceTopicOverrideEntity {

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
    /**
     * 主题槽位
     */
    private String topicSlot;
    /**
     * 主题值
     */
    private String topicValue;

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

    public String getTopicSlot() {
        return topicSlot;
    }

    public void setTopicSlot(String topicSlot) {
        this.topicSlot = topicSlot;
    }

    public String getTopicValue() {
        return topicValue;
    }

    public void setTopicValue(String topicValue) {
        this.topicValue = topicValue;
    }
}
