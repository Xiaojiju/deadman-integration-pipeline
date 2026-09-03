package com.mtfm.gateway.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关宿主配置（{@code gateway.*}）。
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Mqtt mqtt = new Mqtt();
    private final Modbus modbus = new Modbus();
    private final Northbound northbound = new Northbound();

    public Mqtt getMqtt() {
        return mqtt;
    }

    public Modbus getModbus() {
        return modbus;
    }

    public Northbound getNorthbound() {
        return northbound;
    }

    public static class Mqtt {

        /**
         * 传输实现：{@code paho}（真实 Broker）或 {@code memory}（进程内回环）。
         */
        private String transport = "paho";

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }
    }

    public static class Modbus {

        /**
         * 总线实现：{@code real}（同时挂 TCP + RTU，由通道 transport 分流）
         * 或 {@code memory}（进程内格子，单测用）。
         */
        private String mode = "real";
        private int connectTimeoutMs = 3000;
        private int requestTimeoutMs = 3000;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean memory() {
            return "memory".equalsIgnoreCase(mode);
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }

    public static class Northbound {

        private final NorthboundMqtt mqtt = new NorthboundMqtt();
        private final NorthboundHttp http = new NorthboundHttp();

        public NorthboundMqtt getMqtt() {
            return mqtt;
        }

        public NorthboundHttp getHttp() {
            return http;
        }
    }

    public static class NorthboundMqtt {

        private boolean enabled = false;
        private String transport = "paho";
        private String url = "";
        private String commandTopic = "gw/+/command";
        private String responseTopic = "gw/{deviceId}/response";
        private String telemetryTopic = "gw/{deviceId}/telemetry";
        private String clientId = "gateway-northbound";
        private String username;
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean ready() {
            if (!enabled) {
                return false;
            }
            if ("memory".equalsIgnoreCase(transport)) {
                return true;
            }
            return url != null && !url.isBlank();
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCommandTopic() {
            return commandTopic;
        }

        public void setCommandTopic(String commandTopic) {
            this.commandTopic = commandTopic;
        }

        public String getResponseTopic() {
            return responseTopic;
        }

        public void setResponseTopic(String responseTopic) {
            this.responseTopic = responseTopic;
        }

        public String getTelemetryTopic() {
            return telemetryTopic;
        }

        public void setTelemetryTopic(String telemetryTopic) {
            this.telemetryTopic = telemetryTopic;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class NorthboundHttp {

        private String webhookUrl = "";
        private int timeoutMs = 3000;
        private int maxAttempts = 2;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
