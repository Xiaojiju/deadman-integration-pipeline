package com.mtfm.gateway.catalog.dto;

/**
 * 更新北向双通道。密码传掩码则保留原值。
 * NorthboundWriteRequest
 * 
 * @param mqttEnabled        MQTT 通道是否启用
 * @param mqttTransport      MQTT 通道传输协议
 * @param mqttUrl            MQTT 通道地址
 * @param mqttCommandTopic   MQTT 通道命令主题
 * @param mqttResponseTopic  MQTT 通道响应主题
 * @param mqttTelemetryTopic MQTT 通道遥测主题
 * @param mqttClientId       MQTT 通道客户端 ID
 * @param mqttUsername       MQTT 通道用户名
 * @param mqttPassword       MQTT 通道密码
 * @param httpEnabled        HTTP 通道是否启用
 * @param httpWebhookUrl     HTTP 通道 Webhook URL
 * @param httpTimeoutMs      HTTP 通道超时时间
 * @param httpMaxAttempts    HTTP 通道最大重试次数
 */
public record NorthboundWriteRequest(
        Boolean mqttEnabled,
        String mqttTransport,
        String mqttUrl,
        String mqttCommandTopic,
        String mqttResponseTopic,
        String mqttTelemetryTopic,
        String mqttClientId,
        String mqttUsername,
        String mqttPassword,
        Boolean httpEnabled,
        String httpWebhookUrl,
        Integer httpTimeoutMs,
        Integer httpMaxAttempts) {
}
