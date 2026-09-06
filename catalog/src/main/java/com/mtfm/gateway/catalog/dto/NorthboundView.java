package com.mtfm.gateway.catalog.dto;

/**
 * 北向双通道配置视图。
 * NorthboundView
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
 * @param mqttPasswordSet    MQTT 通道密码是否设置
 * @param httpEnabled        HTTP 通道是否启用
 * @param httpWebhookUrl     HTTP 通道 Webhook URL
 * @param httpTimeoutMs      HTTP 通道超时时间
 * @param httpMaxAttempts    HTTP 通道最大重试次数
 * @param mqttLive           MQTT 通道是否在线
 * @param mqttError          MQTT 通道错误信息
 * @param httpLive           HTTP 通道是否在线
 */
public record NorthboundView(
        boolean mqttEnabled,
        String mqttTransport,
        String mqttUrl,
        String mqttCommandTopic,
        String mqttResponseTopic,
        String mqttTelemetryTopic,
        String mqttClientId,
        String mqttUsername,
        String mqttPassword,
        boolean mqttPasswordSet,
        boolean httpEnabled,
        String httpWebhookUrl,
        int httpTimeoutMs,
        int httpMaxAttempts,
        boolean mqttLive,
        String mqttError,
        boolean httpLive) {
}
