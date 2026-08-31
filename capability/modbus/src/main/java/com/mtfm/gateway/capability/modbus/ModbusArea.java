package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 区。
 */
public enum ModbusArea {
    HOLDING,
    INPUT,
    COIL,
    DISCRETE;

    public boolean numeric() {
        return this == HOLDING || this == INPUT;
    }

    public static ModbusArea parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HOLDING;
        }
        return ModbusArea.valueOf(raw.trim().toUpperCase());
    }
}
