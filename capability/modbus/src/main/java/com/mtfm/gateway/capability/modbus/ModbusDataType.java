package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 读写数据类型。
 */
public enum ModbusDataType {
    INT16,
    BOOLEAN;

    public static ModbusDataType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return INT16;
        }
        return ModbusDataType.valueOf(raw.trim().toUpperCase());
    }
}
