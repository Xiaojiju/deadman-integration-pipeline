package com.mtfm.gateway.capability.modbus.rtu;

import java.io.IOException;

/**
 * RTU 串口字节流。生产用 jSerialComm，单测用回环队列。
 */
public interface ModbusSerialPort extends AutoCloseable {

    void write(byte[] frame) throws IOException;

    int read(byte[] buffer, int offset, int length, int timeoutMs) throws IOException;

    @Override
    void close();
}
