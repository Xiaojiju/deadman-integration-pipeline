package com.mtfm.gateway.capability.cloud;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 北向独立 Paho 客户端。不得传入南向设备通道的 client。
 */
public final class PahoNorthboundMqttSession implements NorthboundMqttSession {

    private static final Logger LOG = LoggerFactory.getLogger(PahoNorthboundMqttSession.class);
    private static final int QOS = 1;

    private final String serverUri;
    private final String clientId;
    private final String username;
    private final String password;
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final NorthboundSubscriptionIndex index = new NorthboundSubscriptionIndex();
    private volatile MqttClient client;

    public PahoNorthboundMqttSession(String serverUri, String clientId, String username, String password) {
        if (serverUri == null || serverUri.isBlank()) {
            throw new IllegalArgumentException("北向 MQTT url 不能为空");
        }
        this.serverUri = serverUri.trim();
        this.clientId = (clientId == null || clientId.isBlank()) ? "gateway-northbound" : clientId.trim();
        this.username = blankToNull(username);
        this.password = blankToNull(password);
    }

    @Override
    public synchronized void start() {
        if (client != null && client.isConnected()) {
            return;
        }
        MqttClient created;
        try {
            created = new MqttClient(serverUri, clientId, new MemoryPersistence());
        } catch (MqttException ex) {
            throw new IllegalStateException("北向 MQTT 客户端创建失败", ex);
        }
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);
            if (username != null) {
                options.setUserName(username);
            }
            if (password != null) {
                options.setPassword(password.toCharArray());
            }
            created.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOG.warn("北向 MQTT 断开: {}", cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    index.dispatch(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            created.connect(options);
            for (Subscription subscription : subscriptions) {
                created.subscribe(subscription.filter(), QOS);
            }
            this.client = created;
            created = null;
            LOG.info("北向 MQTT 已连接 uri={} clientId={}", serverUri, clientId);
        } catch (MqttException ex) {
            throw new IllegalStateException("北向 MQTT 连接失败: " + serverUri, ex);
        } finally {
            if (created != null) {
                try {
                    created.close();
                } catch (MqttException ignored) {
                }
            }
        }
    }

    @Override
    public void publish(String topic, String payload) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("北向 MQTT 未连接");
        }
        try {
            MqttMessage message = new MqttMessage(
                    payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(QOS);
            current.publish(topic, message);
        } catch (MqttException ex) {
            throw new IllegalStateException("北向 MQTT 发布失败 topic=" + topic, ex);
        }
    }

    @Override
    public synchronized void subscribe(String topicFilter, BiConsumer<String, String> handler) {
        subscriptions.add(new Subscription(topicFilter, handler));
        index.add(topicFilter, handler);
        MqttClient current = client;
        if (current != null && current.isConnected()) {
            try {
                current.subscribe(topicFilter, QOS);
            } catch (MqttException ex) {
                throw new IllegalStateException("北向 MQTT 订阅失败 topic=" + topicFilter, ex);
            }
        }
    }

    @Override
    public synchronized void close() {
        MqttClient current = client;
        client = null;
        index.clear();
        if (current == null) {
            return;
        }
        try {
            if (current.isConnected()) {
                current.disconnect();
            }
            current.close();
        } catch (MqttException ex) {
            LOG.warn("北向 MQTT 关闭异常: {}", ex.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Subscription(String filter, BiConsumer<String, String> handler) {
    }
}
