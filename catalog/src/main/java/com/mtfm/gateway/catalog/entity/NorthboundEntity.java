package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 北向双通道配置，全库一行，主键固定 {@link #DEFAULT_ID}。
 */
@TableName("gw_northbound")
public class NorthboundEntity {

    public static final String DEFAULT_ID = "default";

    @TableId
    private String id = DEFAULT_ID;
    /** MQTT 通道是否启用 */
    private Boolean mqttEnabled;
    /** MQTT 通道传输协议 */
    private String mqttTransport;
    /** MQTT 通道地址 */
    private String mqttUrl;
    /** MQTT 通道命令主题 */
    private String mqttCommandTopic;
    /** MQTT 通道响应主题 */
    private String mqttResponseTopic;
    /** MQTT 通道遥测主题 */
    private String mqttTelemetryTopic;
    /** MQTT 通道客户端 ID */
    private String mqttClientId;
    /** MQTT 通道用户名 */
    private String mqttUsername;
    /** MQTT 通道密码 */
    private String mqttPassword;
    /** HTTP 通道是否启用 */
    private Boolean httpEnabled;
    /** HTTP 通道 Webhook URL */
    private String httpWebhookUrl;
    /** HTTP 通道超时时间 */
    private Integer httpTimeoutMs;
    /** HTTP 通道最大重试次数 */
    private Integer httpMaxAttempts;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getMqttEnabled() {
        return mqttEnabled;
    }

    public void setMqttEnabled(Boolean mqttEnabled) {
        this.mqttEnabled = mqttEnabled;
    }

    public String getMqttTransport() {
        return mqttTransport;
    }

    public void setMqttTransport(String mqttTransport) {
        this.mqttTransport = mqttTransport;
    }

    public String getMqttUrl() {
        return mqttUrl;
    }

    public void setMqttUrl(String mqttUrl) {
        this.mqttUrl = mqttUrl;
    }

    public String getMqttCommandTopic() {
        return mqttCommandTopic;
    }

    public void setMqttCommandTopic(String mqttCommandTopic) {
        this.mqttCommandTopic = mqttCommandTopic;
    }

    public String getMqttResponseTopic() {
        return mqttResponseTopic;
    }

    public void setMqttResponseTopic(String mqttResponseTopic) {
        this.mqttResponseTopic = mqttResponseTopic;
    }

    public String getMqttTelemetryTopic() {
        return mqttTelemetryTopic;
    }

    public void setMqttTelemetryTopic(String mqttTelemetryTopic) {
        this.mqttTelemetryTopic = mqttTelemetryTopic;
    }

    public String getMqttClientId() {
        return mqttClientId;
    }

    public void setMqttClientId(String mqttClientId) {
        this.mqttClientId = mqttClientId;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }

    public Boolean getHttpEnabled() {
        return httpEnabled;
    }

    public void setHttpEnabled(Boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }

    public String getHttpWebhookUrl() {
        return httpWebhookUrl;
    }

    public void setHttpWebhookUrl(String httpWebhookUrl) {
        this.httpWebhookUrl = httpWebhookUrl;
    }

    public Integer getHttpTimeoutMs() {
        return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(Integer httpTimeoutMs) {
        this.httpTimeoutMs = httpTimeoutMs;
    }

    public Integer getHttpMaxAttempts() {
        return httpMaxAttempts;
    }

    public void setHttpMaxAttempts(Integer httpMaxAttempts) {
        this.httpMaxAttempts = httpMaxAttempts;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
