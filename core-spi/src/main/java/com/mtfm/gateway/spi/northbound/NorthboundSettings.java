package com.mtfm.gateway.spi.northbound;

/**
 * 北向运行时配置（密码已解密）。由 catalog 落库，宿主热切换会话与扇出。
 */
public record NorthboundSettings(
        boolean mqttEnabled,
        String mqttTransport,
        String mqttUrl,
        String mqttCommandTopic,
        String mqttResponseTopic,
        String mqttTelemetryTopic,
        String mqttClientId,
        String mqttUsername,
        String mqttPassword,
        boolean httpEnabled,
        String httpWebhookUrl,
        int httpTimeoutMs,
        int httpMaxAttempts
) {

    public static final String TRANSPORT_PAHO = "paho";
    public static final String TRANSPORT_MEMORY = "memory";
    public static final String DEFAULT_COMMAND_TOPIC = "gw/+/command";
    public static final String DEFAULT_RESPONSE_TOPIC = "gw/{deviceId}/response";
    public static final String DEFAULT_TELEMETRY_TOPIC = "gw/{deviceId}/telemetry";
    public static final String DEFAULT_CLIENT_ID = "gateway-northbound";

    public static NorthboundSettings disabled() {
        return new NorthboundSettings(
                false, TRANSPORT_PAHO, "",
                DEFAULT_COMMAND_TOPIC, DEFAULT_RESPONSE_TOPIC, DEFAULT_TELEMETRY_TOPIC,
                DEFAULT_CLIENT_ID, "", "",
                false, "", 3000, 2);
    }

    public boolean mqttReady() {
        if (!mqttEnabled) {
            return false;
        }
        if (TRANSPORT_MEMORY.equalsIgnoreCase(mqttTransport)) {
            return true;
        }
        return mqttUrl != null && !mqttUrl.isBlank();
    }

    public boolean httpReady() {
        return httpEnabled && httpWebhookUrl != null && !httpWebhookUrl.isBlank();
    }

    public boolean memoryTransport() {
        return TRANSPORT_MEMORY.equalsIgnoreCase(mqttTransport);
    }

    public String commandTopicOrDefault() {
        return blankToDefault(mqttCommandTopic, DEFAULT_COMMAND_TOPIC);
    }

    public String responseTopicOrDefault() {
        return blankToDefault(mqttResponseTopic, DEFAULT_RESPONSE_TOPIC);
    }

    public String telemetryTopicOrDefault() {
        return blankToDefault(mqttTelemetryTopic, DEFAULT_TELEMETRY_TOPIC);
    }

    public String clientIdOrDefault() {
        return blankToDefault(mqttClientId, DEFAULT_CLIENT_ID);
    }

    public int timeoutMsOrDefault() {
        return httpTimeoutMs > 0 ? httpTimeoutMs : 3000;
    }

    public int maxAttemptsOrDefault() {
        return httpMaxAttempts > 0 ? httpMaxAttempts : 2;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
