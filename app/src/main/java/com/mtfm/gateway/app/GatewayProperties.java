package com.mtfm.gateway.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关宿主配置（{@code gateway.*}）。
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Mqtt mqtt = new Mqtt();
    private final Modbus modbus = new Modbus();

    public Mqtt getMqtt() {
        return mqtt;
    }

    public Modbus getModbus() {
        return modbus;
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
}
