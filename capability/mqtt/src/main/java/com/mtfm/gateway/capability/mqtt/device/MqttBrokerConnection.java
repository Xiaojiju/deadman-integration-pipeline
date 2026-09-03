package com.mtfm.gateway.capability.mqtt.device;

import com.mtfm.gateway.spi.model.Attributes;

import java.net.URI;
import java.util.Map;

/**
 * MQTT Broker 连接参数（来自通道 connection 属性）。
 *
 * <p>host 必填；未配置时失败而不是连到本机 1883。port 缺省为 1883。
 */
public record MqttBrokerConnection(
        String host,
        int port,
        String username,
        String password
) {

    public MqttBrokerConnection {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("MQTT 通道缺少 host");
        }
        if (port <= 0) {
            port = 1883;
        }
    }

    /** Paho 使用的 tcp:// URI。 */
    public String serverUri() {
        return "tcp://" + host + ":" + port;
    }

    public static MqttBrokerConnection fromAttributes(Attributes connection) {
        return fromMap(connection == null ? Map.of() : connection.values());
    }

    public static MqttBrokerConnection fromMap(Map<String, Object> connection) {
        if (connection == null || connection.isEmpty()) {
            throw new IllegalArgumentException("MQTT 通道 connection 为空");
        }
        Object brokerObj = connection.get("broker");
        if (brokerObj != null && !String.valueOf(brokerObj).isBlank()) {
            return parseBrokerUri(String.valueOf(brokerObj).trim(), connection);
        }
        String host = stringOr(connection.get("host"), null);
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("MQTT 通道缺少 host");
        }
        int port = parsePort(connection.get("port"), 1883);
        String username = blankToNull(stringOr(connection.get("username"), null));
        String password = blankToNull(stringOr(connection.get("password"), null));
        return new MqttBrokerConnection(host, port, username, password);
    }

    private static MqttBrokerConnection parseBrokerUri(String broker, Map<String, Object> connection) {
        try {
            URI uri = URI.create(broker.replaceFirst("^mqtt://", "tcp://"));
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("MQTT broker URI 缺少 host: " + broker);
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 1883;
            String username = blankToNull(stringOr(connection.get("username"), null));
            String password = blankToNull(stringOr(connection.get("password"), null));
            return new MqttBrokerConnection(host, port, username, password);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析 MQTT broker URI: " + broker, ex);
        }
    }

    private static int parsePort(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stringOr(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? fallback : text;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
