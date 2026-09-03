package com.mtfm.gateway.capability.modbus;

import java.util.ArrayList;
import java.util.List;

/**
 * TCP / RTU 共用的区读写，子类只负责组帧与会话。
 */
public abstract class AbstractModbusBus implements ModbusBus {

    private static final int MAX_REGISTER_QUANTITY = 125;
    private static final int MAX_COIL_QUANTITY = 2000;

    protected abstract byte[] transact(ModbusChannel channel, int unitId, byte[] pdu);

    @Override
    public Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType) {
        return readNumerics(channel, unitId, area, offset, 1, dataType).getFirst();
    }

    @Override
    public List<Number> readNumerics(ModbusChannel channel, int unitId, ModbusArea area, int offset, int quantity,
            ModbusDataType dataType) {
        if (!area.numeric()) {
            throw new ModbusException("区 " + area + " 不能按寄存器读");
        }
        int remaining = Math.max(1, quantity);
        int cursor = offset;
        List<Number> values = new ArrayList<>(remaining);
        int fc = area == ModbusArea.INPUT ? ModbusPdu.FC_READ_INPUT : ModbusPdu.FC_READ_HOLDING;
        while (remaining > 0) {
            int chunk = Math.min(remaining, MAX_REGISTER_QUANTITY);
            byte[] pdu = transact(channel, unitId, ModbusPdu.readQuantity(fc, cursor, chunk));
            for (int register : ModbusPdu.readRegisterValues(pdu, fc, chunk)) {
                if (dataType == ModbusDataType.BOOLEAN) {
                    values.add(register != 0 ? 1 : 0);
                } else {
                    values.add((short) register);
                }
            }
            cursor += chunk;
            remaining -= chunk;
        }
        return values;
    }

    @Override
    public boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset) {
        return readBooleans(channel, unitId, area, offset, 1).getFirst();
    }

    @Override
    public List<Boolean> readBooleans(ModbusChannel channel, int unitId, ModbusArea area, int offset, int quantity) {
        if (area.numeric()) {
            List<Boolean> flags = new ArrayList<>();
            for (Number value : readNumerics(channel, unitId, area, offset, quantity, ModbusDataType.BOOLEAN)) {
                flags.add(value.intValue() != 0);
            }
            return flags;
        }
        int remaining = Math.max(1, quantity);
        int cursor = offset;
        List<Boolean> values = new ArrayList<>(remaining);
        int fc = area == ModbusArea.DISCRETE ? ModbusPdu.FC_READ_DISCRETE : ModbusPdu.FC_READ_COILS;
        while (remaining > 0) {
            int chunk = Math.min(remaining, MAX_COIL_QUANTITY);
            byte[] pdu = transact(channel, unitId, ModbusPdu.readQuantity(fc, cursor, chunk));
            values.addAll(ModbusPdu.readCoilValues(pdu, fc, chunk));
            cursor += chunk;
            remaining -= chunk;
        }
        return values;
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
