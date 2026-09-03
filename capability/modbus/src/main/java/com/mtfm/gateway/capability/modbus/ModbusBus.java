package com.mtfm.gateway.capability.modbus;

/**
 * Modbus 读写端口，按 host:port 会话键管理 TCP 连接引用计数。
 *
 * <p>
 * {@link #retain(ModbusChannel)} 增加引用，{@link #release(ModbusChannel)} 减少引用，
 * 计数归零时关闭底层连接。{@link ModbusExecutor} bind/unbind 时调用。
 */
public interface ModbusBus extends AutoCloseable {

    /**
     * 读取数字量
     * 
     * @param channel  通道
     * @param unitId   设备地址
     * @param area     寄存器区域 {@link ModbusArea}
     * @param offset   寄存器偏移量
     * @param dataType 数据类型 {@link ModbusDataType}
     * @return 读取到的值
     */
    Number readNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType)
            throws ModbusException;

    /**
     * 读取布尔量
     * 
     * @param channel 通道
     * @param unitId  设备地址
     * @param area    寄存器区域 {@link ModbusArea}
     * @param offset  寄存器偏移量
     * @return 读取到的值
     */
    boolean readBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset);

    /**
     * 写入数字量
     * 
     * @param channel  通道
     * @param unitId   设备地址
     * @param area     寄存器区域 {@link ModbusArea}
     * @param offset   寄存器偏移量
     * @param dataType 数据类型 {@link ModbusDataType}
     * @param value    写入的值
     */
    void writeNumeric(ModbusChannel channel, int unitId, ModbusArea area, int offset, ModbusDataType dataType,
            Number value);

    /**
     * 写入布尔量
     * 
     * @param channel 通道
     * @param unitId  设备地址
     * @param area    寄存器区域 {@link ModbusArea}
     * @param offset  寄存器偏移量
     * @param value   写入的值
     */
    void writeBoolean(ModbusChannel channel, int unitId, ModbusArea area, int offset, boolean value);

    /**
     * 增加引用计数
     * 
     * @param channel 通道
     */
    default void retain(ModbusChannel channel) {
    }

    /**
     * 减少引用计数
     * 
     * @param channel 通道
     */
    default void release(ModbusChannel channel) {
    }

    /**
     * 获取引用计数
     * 
     * @param sessionKey 会话键
     * @return 引用计数
     */
    default int refCount(String sessionKey) {
        return 0;
    }

    /**
     * 连续读数字量；默认循环单次读。TCP/RTU 实现一次 PDU。
     */
    default java.util.List<Number> readNumerics(ModbusChannel channel, int unitId, ModbusArea area, int offset,
            int quantity, ModbusDataType dataType) {
        int count = Math.max(1, quantity);
        java.util.List<Number> values = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readNumeric(channel, unitId, area, offset + i, dataType));
        }
        return values;
    }

    /**
     * 连续读线圈/离散量；默认循环单次读。
     */
    default java.util.List<Boolean> readBooleans(ModbusChannel channel, int unitId, ModbusArea area, int offset,
            int quantity) {
        int count = Math.max(1, quantity);
        java.util.List<Boolean> values = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readBoolean(channel, unitId, area, offset + i));
        }
        return values;
    }

    @Override
    default void close() {
    }
}
