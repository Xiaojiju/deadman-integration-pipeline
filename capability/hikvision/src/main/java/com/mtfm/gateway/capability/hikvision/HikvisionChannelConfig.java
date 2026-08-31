package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.spi.model.Attributes;

/**
 * 海康 ISAPI 通道连接配置，来自 catalog 通道 connection JSON。
 */
public record HikvisionChannelConfig(
        String channelId,
        String host,
        int port,
        String username,
        String password) {

    private static final int DEFAULT_PORT = 80;

    public HikvisionChannelConfig {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (port <= 0) {
            port = DEFAULT_PORT;
        }
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }

    /**
     * 从 bind 时的 connection Attributes 解析。
     * 
     * @param channelId  通道 ID
     * @param connection 连接 Attributes
     * @return 通道配置
     */
    public static HikvisionChannelConfig from(String channelId, Attributes connection) {
        String host = connection.get("host")
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("connection.host 必填"));
        int port = connection.get("port")
                .map(value -> Integer.parseInt(String.valueOf(value)))
                .orElse(DEFAULT_PORT);
        String username = connection.get("username").map(String::valueOf).orElse("");
        String password = connection.get("password").map(String::valueOf).orElse("");
        return new HikvisionChannelConfig(channelId, host, port, username, password);
    }

    /**
     * 构建 ISAPI 根 URL，例如 {@code http://192.168.1.10:80}。
     * 
     * @return ISAPI 根 URL
     */
    public String baseUrl() {
        String normalized = host.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        return normalized + ":" + port;
    }
}
