package com.mtfm.gateway.capability.modbus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存总线：按 sessionKey 引用计数，供单测与无真设备验收。
 */
public final class InMemoryModbusBus implements ModbusBus {

    private final ConcurrentHashMap<String, AtomicInteger> refs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> cells = new ConcurrentHashMap<>();

    @Override
    public void retain(ModbusChannel channel) {
        refs.computeIfAbsent(channel.sessionKey(), key -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void release(ModbusChannel channel) {
        AtomicInteger count = refs.get(channel.sessionKey());
        if (count == null) {
            return;
        }
        if (count.decrementAndGet() <= 0) {
            refs.remove(channel.sessionKey());
            cells.keySet().removeIf(key -> key.startsWith(channel.sessionKey() + "|"));
        }
    }

    @Override
    public int refCount(String sessionKey) {
        AtomicInteger count = refs.get(sessionKey);
        return count == null ? 0 : count.get();
    }

    @Override
    public Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType) {
        Object value = cells.get(cellKey(channel, unitId, area, offset));
        if (value instanceof Number number) {
            return number;
        }
        return 0;
    }

    @Override
    public boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset) {
        Object value = cells.get(cellKey(channel, unitId, area, offset));
        return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() != 0);
    }

    @Override
    public void writeNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType,
            Number value) {
        cells.put(cellKey(channel, unitId, area, offset), value);
    }

    @Override
    public void writeBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset, boolean value) {
        cells.put(cellKey(channel, unitId, area, offset), value);
    }

    private static String cellKey(ModbusChannel channel, int unitId, ModbusArea area, int offset) {
        return channel.sessionKey() + "|" + unitId + "|" + area + "|" + offset;
    }
}
