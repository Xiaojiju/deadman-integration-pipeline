package com.mtfm.gateway.spi.port;

/**
 * 设备 READ 功能解析出的 MQTT 订阅路由。
 *
 * @param topic      实际订阅 topic
 * @param functionId 功能 ID
 */
public record MqttSubscribeRoute(String topic, String functionId) {
}
