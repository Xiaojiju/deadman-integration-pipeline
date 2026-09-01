package com.mtfm.gateway.capability.mqtt.device;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Eclipse Paho MQTT 传输：按 channelId 共享 {@link MqttClient}，引用计数管理连接生命周期。
 */
public final class PahoMqttTransport implements MqttTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PahoMqttTransport.class);
    private static final int QOS = 1;

    private final ConcurrentHashMap<String, ChannelSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void retain(String channelId, MqttBrokerConnection connection) {
        ChannelSession session = sessions.computeIfAbsent(channelId, ChannelSession::new);
        if (session.refs.incrementAndGet() == 1) {
            session.connection = connection;
            connect(session);
        }
    }

    @Override
    public void release(String channelId) {
        ChannelSession session = sessions.get(channelId);
        if (session == null) {
            return;
        }
        if (session.refs.decrementAndGet() <= 0) {
            sessions.remove(channelId, session);
            disconnect(session);
        }
    }

    @Override
    public int refCount(String channelId) {
        ChannelSession session = sessions.get(channelId);
        return session == null ? 0 : session.refs.get();
    }

    @Override
    public void publish(String channelId, String topic, String payload) {
        ChannelSession session = sessions.get(channelId);
        if (session == null || session.client == null || !session.client.isConnected()) {
            throw new IllegalStateException("MQTT 通道未连接: " + channelId);
        }
        try {
            MqttMessage message = new MqttMessage(payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(QOS);
            session.client.publish(topic, message);
        } catch (MqttException ex) {
            throw new IllegalStateException("MQTT 发布失败: " + channelId + " topic=" + topic, ex);
        }
    }

    @Override
    public void subscribe(String channelId, String topic, BiConsumer<String, String> handler) {
        ChannelSession session = sessions.computeIfAbsent(channelId, ChannelSession::new);
        session.topicHandlers
                .computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>())
                .add(handler);
        if (session.client != null && session.client.isConnected()) {
            subscribeTopic(session, topic);
        }
    }

    @Override
    public void close() {
        List<ChannelSession> snapshot = new ArrayList<>(sessions.values());
        sessions.clear();
        for (ChannelSession session : snapshot) {
            disconnect(session);
        }
    }

    private void connect(ChannelSession session) {
        MqttBrokerConnection conn = session.connection;
        if (conn == null) {
            conn = new MqttBrokerConnection("localhost", 1883, null, null);
            session.connection = conn;
        }
        try {
            String clientId = "gateway-" + sanitizeClientId(session.channelId);
            MqttClient client = new MqttClient(conn.serverUri(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);
            if (conn.username() != null) {
                options.setUserName(conn.username());
            }
            if (conn.password() != null) {
                options.setPassword(conn.password().toCharArray());
            }
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOG.warn("MQTT 连接断开 channel={}: {}", session.channelId, cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    dispatch(session, topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.connect(options);
            session.client = client;
            for (String topic : session.topicHandlers.keySet()) {
                subscribeTopic(session, topic);
            }
            LOG.info("MQTT 已连接 channel={} uri={}", session.channelId, conn.serverUri());
        } catch (MqttException ex) {
            throw new IllegalStateException("MQTT 连接失败 channel=" + session.channelId, ex);
        }
    }

    private void subscribeTopic(ChannelSession session, String topic) {
        try {
            session.client.subscribe(topic, QOS);
        } catch (MqttException ex) {
            throw new IllegalStateException("MQTT 订阅失败 channel=" + session.channelId + " topic=" + topic, ex);
        }
    }

    private static void dispatch(ChannelSession session, String topic, MqttMessage message) {
        CopyOnWriteArrayList<BiConsumer<String, String>> handlers = session.topicHandlers.get(topic);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        handlers.forEach(handler -> handler.accept(topic, payload));
    }

    private static void disconnect(ChannelSession session) {
        MqttClient client = session.client;
        session.client = null;
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            LOG.info("MQTT 已断开 channel={}", session.channelId);
        } catch (MqttException ex) {
            LOG.warn("MQTT 断开异常 channel={}: {}", session.channelId, ex.getMessage());
        }
    }

    private static String sanitizeClientId(String channelId) {
        return channelId.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private static final class ChannelSession {
        private final String channelId;
        private final AtomicInteger refs = new AtomicInteger();
        private volatile MqttBrokerConnection connection;
        private volatile MqttClient client;
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<BiConsumer<String, String>>> topicHandlers =
                new ConcurrentHashMap<>();

        private ChannelSession(String channelId) {
            this.channelId = channelId;
        }
    }
}
