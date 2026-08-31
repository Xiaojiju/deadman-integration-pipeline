package com.mtfm.gateway.capability.modbus;

/**
 * 共享 Modbus TCP 通道键，同一 host:port 多从站复用。
 *
 * <p>由 {@link ModbusBus#retain} / {@link ModbusBus#release} 管理引用计数与连接生命周期。
 */
public record ModbusChannel(String channelId, String host, int port) {

    public ModbusChannel {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (port <= 0) {
            port = 502;
        }
    }

    public String sessionKey() {
        return host + ":" + port;
    }
}
