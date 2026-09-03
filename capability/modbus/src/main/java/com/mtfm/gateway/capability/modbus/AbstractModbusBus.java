package com.mtfm.gateway.capability.modbus;

/**
 * TCP / RTU 共用的区读写，子类只负责组帧与会话。
 */
public abstract class AbstractModbusBus implements ModbusBus {

    protected abstract byte[] transact(ModbusChannel channel, int unitId, byte[] pdu);

    @Override
    public Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType) {
        if (!area.numeric()) {
            throw new ModbusException("区 " + area + " 不能按寄存器读");
        }
        int fc = area == ModbusArea.INPUT ? ModbusPdu.FC_READ_INPUT : ModbusPdu.FC_READ_HOLDING;
        byte[] pdu = transact(channel, unitId, ModbusPdu.readQuantity(fc, offset, 1));
        int register = ModbusPdu.readRegisterValue(pdu, fc);
        if (dataType == ModbusDataType.BOOLEAN) {
            return register != 0 ? 1 : 0;
        }
        return (short) register;
    }

    @Override
    public boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset) {
        if (area.numeric()) {
            return readNumeric(channel, unitId, area, offset, ModbusDataType.BOOLEAN).intValue() != 0;
        }
        int fc = area == ModbusArea.DISCRETE ? ModbusPdu.FC_READ_DISCRETE : ModbusPdu.FC_READ_COILS;
        byte[] pdu = transact(channel, unitId, ModbusPdu.readQuantity(fc, offset, 1));
        if (pdu.length < 3 || (pdu[1] & 0xFF) < 1) {
            throw new ModbusException("线圈/离散量响应过短");
        }
        return (pdu[2] & 0x01) != 0;
    }

    @Override
    public void writeNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType,
            Number value) {
        if (area != ModbusArea.HOLDING) {
            throw new ModbusException("区 " + area + " 不允许写寄存器");
        }
        int raw = value == null ? 0 : value.intValue();
        if (dataType == ModbusDataType.BOOLEAN) {
            raw = raw != 0 ? 1 : 0;
        }
        transact(channel, unitId, ModbusPdu.writeSingle(ModbusPdu.FC_WRITE_REGISTER, offset, raw & 0xFFFF));
    }

    @Override
    public void writeBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset, boolean value) {
        if (area.numeric()) {
            writeNumeric(channel, unitId, area, offset, ModbusDataType.BOOLEAN, value ? 1 : 0);
            return;
        }
        if (area != ModbusArea.COIL) {
            throw new ModbusException("区 " + area + " 不允许写线圈");
        }
        transact(channel, unitId, ModbusPdu.writeSingle(ModbusPdu.FC_WRITE_COIL, offset, value ? 0xFF00 : 0x0000));
    }
}
