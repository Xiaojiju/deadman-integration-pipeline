package com.mtfm.gateway.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.mqtt")
public class GatewayMqttProperties {

    /**
     * 传输实现：{@code paho}（真实 Broker）或 {@code memory}（进程内回环，单测/无 Broker 开发）。
     */
    private String transport = "paho";

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }
}
