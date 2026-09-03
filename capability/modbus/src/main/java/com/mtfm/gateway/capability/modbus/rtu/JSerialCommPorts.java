package com.mtfm.gateway.capability.modbus.rtu;

import com.fazecast.jSerialComm.SerialPort;
import com.mtfm.gateway.capability.modbus.ModbusChannel;

import java.io.IOException;

/**
 * jSerialComm 串口工厂。
 */
final class JSerialCommPorts implements ModbusSerialPorts {

    @Override
    public ModbusSerialPort open(ModbusChannel channel) throws IOException {
        SerialPort port = SerialPort.getCommPort(channel.serialPort());
        port.setComPortParameters(
                channel.baudRate(),
                channel.dataBits(),
                channel.stopBits(),
                parityCode(channel.parity()));
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 50, 0);
        if (!port.openPort()) {
            throw new IOException("打开串口失败: " + channel.serialPort());
        }
        return new JSerialPort(port);
    }

    private static int parityCode(String parity) {
        return switch (parity == null ? "NONE" : parity.toUpperCase()) {
            case "EVEN" -> SerialPort.EVEN_PARITY;
            case "ODD" -> SerialPort.ODD_PARITY;
            case "MARK" -> SerialPort.MARK_PARITY;
            case "SPACE" -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }

    private record JSerialPort(SerialPort port) implements ModbusSerialPort {

        @Override
        public void write(byte[] frame) throws IOException {
            int written = port.writeBytes(frame, frame.length);
            if (written != frame.length) {
                throw new IOException("串口写入不完整 " + written + "/" + frame.length);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length, int timeoutMs) {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, Math.max(1, timeoutMs), 0);
            return port.readBytes(buffer, length, offset);
        }

        @Override
        public void close() {
            port.closePort();
        }
    }
}
