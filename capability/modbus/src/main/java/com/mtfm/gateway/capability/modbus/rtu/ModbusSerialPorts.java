package com.mtfm.gateway.capability.modbus.rtu;

import com.mtfm.gateway.capability.modbus.ModbusChannel;

import java.io.IOException;

/**
 * 按通道打开串口。
 */
@FunctionalInterface
public interface ModbusSerialPorts {

    ModbusSerialPort open(ModbusChannel channel) throws IOException;
}
