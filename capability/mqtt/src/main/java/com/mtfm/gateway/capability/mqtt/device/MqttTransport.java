package com.mtfm.gateway.capability.mqtt.device;

import java.util.function.BiConsumer;

/**
 * 南向 MQTT 传输抽象，生产可接 Paho，单测用 {@link InMemoryMqttTransport}。
 *
 * <p>
 * {@link #retain(String, MqttBrokerConnection)} / {@link #release(String)} 按 channelId 引用计数管理
 * Broker 会话。
 */
public interface MqttTransport extends AutoCloseable {

    /**
     * 保留通道并建立（或复用）Broker 会话。
     */
    void retain(String channelId, MqttBrokerConnection connection);

    /**
     * 释放通道
     * 
     * @param channelId 通道 ID
     * @return 是否成功
     */
    void release(String channelId);

    /**
     * 获取通道引用计数
     * 
     * @param channelId 通道 ID
     * @return 引用计数
     */
    int refCount(String channelId);

    /**
     * 发布消息
     * 
     * @param channelId 通道 ID
     * @param topic     主题
     * @param payload   负载
     */
    void publish(String channelId, String topic, String payload);

    /**
     * 订阅消息
     * 
     * @param channelId 通道 ID
     * @param topic     主题
     * @param handler   处理程序
     */
    void subscribe(String channelId, String topic, BiConsumer<String, String> handler);

    /**
     * 取消指定 handler 的订阅。同一 channel/topic 上其他设备的 handler 不受影响；
     * 该 topic 已无 handler 时，生产实现应向 Broker 发送 unsubscribe。
     *
     * @param channelId 通道 ID
     * @param topic     主题
     * @param handler   当初 {@link #subscribe} 传入的同一实例
     */
    void unsubscribe(String channelId, String topic, BiConsumer<String, String> handler);

    @Override
    default void close() {
    }
}
