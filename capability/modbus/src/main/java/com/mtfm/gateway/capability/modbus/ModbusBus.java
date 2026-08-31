package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 读写端口，按 host:port 会话键管理 TCP 连接引用计数。
 *
 * <p>{@link #retain(ModbusChannel)} 增加引用，{@link #release(ModbusChannel)} 减少引用，
 * 计数归零时关闭底层连接。{@link ModbusExecutor} bind/unbind 时调用。
 */
public interface ModbusBus extends AutoCloseable {

    Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType);

    boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset);

    void writeNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType,
            Number value);

    void writeBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset, boolean value);

    default void retain(ModbusChannel channel) {
    }

    default void release(ModbusChannel channel) {
    }

    default int refCount(String sessionKey) {
        return 0;
    }

    @Override
    default void close() {
    }
}
