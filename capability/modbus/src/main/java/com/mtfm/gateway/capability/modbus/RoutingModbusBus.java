package com.mtfm.gateway.capability.modbus;

/**
 * 按通道 transport 分流：TCP → {@link com.mtfm.gateway.capability.modbus.tcp.TcpModbusBus}，
 * RTU → {@link com.mtfm.gateway.capability.modbus.rtu.RtuModbusBus}。
 */
public final class RoutingModbusBus implements ModbusBus {

    private final ModbusBus tcp;
    private final ModbusBus rtu;

    public RoutingModbusBus(ModbusBus tcp, ModbusBus rtu) {
        if (tcp == null || rtu == null) {
            throw new IllegalArgumentException("tcp / rtu 总线不能为空");
        }
        this.tcp = tcp;
        this.rtu = rtu;
    }

    private ModbusBus pick(ModbusChannel channel) {
        return channel != null && channel.rtu() ? rtu : tcp;
    }

    @Override
    public void retain(ModbusChannel channel) {
        pick(channel).retain(channel);
    }

    @Override
    public void release(ModbusChannel channel) {
        pick(channel).release(channel);
    }

    @Override
    public int refCount(String sessionKey) {
        int tcpCount = tcp.refCount(sessionKey);
        return tcpCount > 0 ? tcpCount : rtu.refCount(sessionKey);
    }

    @Override
    public Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType) {
        return pick(channel).readNumeric(channel, unitId, area, offset, dataType);
    }

    @Override
    public boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset) {
        return pick(channel).readBoolean(channel, unitId, area, offset);
    }

    @Override
    public void writeNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType,
            Number value) {
        pick(channel).writeNumeric(channel, unitId, area, offset, dataType, value);
    }

    @Override
    public void writeBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset, boolean value) {
        pick(channel).writeBoolean(channel, unitId, area, offset, value);
    }

    @Override
    public void close() {
        tcp.close();
        rtu.close();
    }
}
