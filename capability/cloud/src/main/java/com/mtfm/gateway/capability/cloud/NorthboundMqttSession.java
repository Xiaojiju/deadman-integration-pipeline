package com.mtfm.gateway.capability.cloud;

import java.util.function.BiConsumer;

/**
 * 北向独立 MQTT 会话。禁止与南向设备通道共用 Broker 连接。
 */
public interface NorthboundMqttSession extends AutoCloseable {

    void start();

    void publish(String topic, String payload);

    void subscribe(String topicFilter, BiConsumer<String, String> handler);

    @Override
    default void close() {
    }
}
