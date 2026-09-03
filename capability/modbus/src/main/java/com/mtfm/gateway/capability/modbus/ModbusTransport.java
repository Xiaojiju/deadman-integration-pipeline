package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 通道传输：TCP 或串口 RTU。产品功能仍是同一套 CONTRACT。
 */
public enum ModbusTransport {
    TCP,
    RTU;

    public static ModbusTransport parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TCP;
        }
        return ModbusTransport.valueOf(raw.trim().toUpperCase());
    }
}
