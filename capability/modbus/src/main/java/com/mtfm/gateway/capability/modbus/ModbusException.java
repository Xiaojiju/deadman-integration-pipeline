package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 读写失败（连接、超时、从站异常码）。
 */
public final class ModbusException extends RuntimeException {

    public ModbusException(String message) {
        super(message);
    }

    public ModbusException(String message, Throwable cause) {
        super(message, cause);
    }
}
