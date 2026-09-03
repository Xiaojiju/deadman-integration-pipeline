package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 应用层 PDU 与 RTU CRC，TCP / RTU 共用。
 */
public final class ModbusPdu {

    public static final int FC_READ_COILS = 0x01;
    public static final int FC_READ_DISCRETE = 0x02;
    public static final int FC_READ_HOLDING = 0x03;
    public static final int FC_READ_INPUT = 0x04;
    public static final int FC_WRITE_COIL = 0x05;
    public static final int FC_WRITE_REGISTER = 0x06;

    private ModbusPdu() {
    }

    public static byte[] readQuantity(int function, int offset, int quantity) {
        return new byte[] {
                (byte) function,
                (byte) (offset >>> 8),
                (byte) offset,
                (byte) (quantity >>> 8),
                (byte) quantity
        };
    }

    public static byte[] writeSingle(int function, int offset, int value) {
        return new byte[] {
                (byte) function,
                (byte) (offset >>> 8),
                (byte) offset,
                (byte) (value >>> 8),
                (byte) value
        };
    }

    public static int readRegisterValue(byte[] pdu, int expectedFc) {
        if (pdu.length < 4 || (pdu[0] & 0xFF) != expectedFc || (pdu[1] & 0xFF) < 2) {
            throw new ModbusException("寄存器响应无效 fc=" + expectedFc);
        }
        return ((pdu[2] & 0xFF) << 8) | (pdu[3] & 0xFF);
    }

    public static void requireSuccess(byte[] pdu) {
        if (pdu != null && pdu.length >= 2 && (pdu[0] & 0x80) != 0) {
            throw new ModbusException(exceptionMessage(pdu[1] & 0xFF));
        }
    }

    public static String exceptionMessage(int code) {
        return switch (code) {
            case 1 -> "从站非法功能";
            case 2 -> "从站非法数据地址";
            case 3 -> "从站非法数据值";
            case 4 -> "从站设备故障";
            default -> "从站异常码 " + code;
        };
    }

    /** Modbus RTU CRC-16，低字节在前。 */
    public static int crc16(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[offset + i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static byte[] wrapRtu(int unitId, byte[] pdu) {
        byte[] frame = new byte[1 + pdu.length + 2];
        frame[0] = (byte) unitId;
        System.arraycopy(pdu, 0, frame, 1, pdu.length);
        int crc = crc16(frame, 0, 1 + pdu.length);
        frame[frame.length - 2] = (byte) crc;
        frame[frame.length - 1] = (byte) (crc >>> 8);
        return frame;
    }

    public static byte[] unwrapRtu(byte[] frame, int unitId) {
        if (frame == null || frame.length < 4) {
            throw new ModbusException("RTU 帧过短");
        }
        int crc = crc16(frame, 0, frame.length - 2);
        int actual = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
        if (crc != actual) {
            throw new ModbusException("RTU CRC 不匹配");
        }
        int unit = frame[0] & 0xFF;
        if (unit != (unitId & 0xFF)) {
            throw new ModbusException("从站号不匹配 expect=" + unitId + " actual=" + unit);
        }
        byte[] pdu = new byte[frame.length - 3];
        System.arraycopy(frame, 1, pdu, 0, pdu.length);
        requireSuccess(pdu);
        return pdu;
    }
}
