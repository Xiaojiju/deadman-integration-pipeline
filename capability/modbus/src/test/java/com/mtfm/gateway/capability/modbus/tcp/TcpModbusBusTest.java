package com.mtfm.gateway.capability.modbus.tcp;

import com.mtfm.gateway.capability.modbus.ModbusArea;
import com.mtfm.gateway.capability.modbus.ModbusCapability;
import com.mtfm.gateway.capability.modbus.ModbusChannel;
import com.mtfm.gateway.capability.modbus.ModbusDataType;
import com.mtfm.gateway.capability.modbus.ModbusException;
import com.mtfm.gateway.capability.modbus.ModbusExecutor;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.FunctionCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpModbusBusTest {

    private LoopbackModbusTcpSlave slave;
    private TcpModbusBus bus;
    private ModbusChannel channel;

    @BeforeEach
    void startSlave() throws IOException {
        slave = LoopbackModbusTcpSlave.start();
        bus = new TcpModbusBus(1000, 1000);
        channel = new ModbusChannel("c1", "127.0.0.1", slave.port());
        bus.retain(channel);
    }

    @AfterEach
    void stop() {
        bus.close();
        slave.close();
    }

    @Test
    void writeAndReadCoilAndHolding() {
        bus.writeBoolean(channel, 1, ModbusArea.COIL, 10, true);
        assertTrue(bus.readBoolean(channel, 1, ModbusArea.COIL, 10));

        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 42);
        assertEquals(42, bus.readNumeric(channel, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16).intValue());
    }

    @Test
    void readHoldingQuantityUsesOnePdu() {
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 11);
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 1, ModbusDataType.INT16, 22);
        var values = bus.readNumerics(channel, 1, ModbusArea.HOLDING, 0, 2, ModbusDataType.INT16);
        assertEquals(List.of((short) 11, (short) 22), values);
    }

    @Test
    void twoUnitIdsShareOneTcpSession() {
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 11);
        bus.writeNumeric(channel, 2, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 22);
        assertEquals(11, bus.readNumeric(channel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(22, bus.readNumeric(channel, 2, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(1, bus.refCount(channel.sessionKey()));
    }

    @Test
    void executorDispatchesCustomFunctionToRealSlave() {
        ModbusExecutor executor = new ModbusExecutor(bus);
        executor.bind(new DeviceEndpointBinding(
                "lamp",
                "c1",
                ModbusCapability.TYPE,
                Attributes.from(Map.of("host", "127.0.0.1", "port", slave.port())),
                Attributes.from(Map.of("slaveId", 1))));

        ExecutionResult written = executor.execute(FunctionCommand.of(
                "lamp",
                "light.switch",
                Map.of("area", "COIL", "offset", 7, "value", true),
                Map.of("accessType", "WRITE")));
        assertEquals(ExecutionStatus.SUCCESS, written.status());

        ExecutionResult read = executor.execute(FunctionCommand.of(
                "lamp",
                "light.status",
                Map.of("area", "COIL", "offset", 7, "quantity", 1),
                Map.of("accessType", "READ")));
        assertEquals(ExecutionStatus.SUCCESS, read.status());
        assertEquals(true, ((List<?>) read.data().values().get("values")).get(0));
        executor.unbind("lamp");
    }

    @Test
    void keepAliveReusesOneTcpConnection() {
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 1);
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 2);
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 3);
        assertEquals(1, slave.acceptCount());
    }

    @Test
    void keepAliveFalseClosesAfterEachCommand() {
        bus.close();
        bus = new TcpModbusBus(1000, 1000);
        ModbusChannel oneShot = new ModbusChannel("oneshot", "127.0.0.1", slave.port(), false);
        bus.retain(oneShot);
        bus.writeNumeric(oneShot, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 11);
        bus.writeNumeric(oneShot, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 22);
        assertEquals(2, slave.acceptCount());
    }

    @Test
    void idleTimeoutReconnectsKeepAliveSession() throws InterruptedException {
        bus.close();
        bus = new TcpModbusBus(1000, 1000, 120);
        ModbusChannel kept = new ModbusChannel("idle", "127.0.0.1", slave.port(), true);
        bus.retain(kept);
        bus.writeNumeric(kept, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 7);
        assertEquals(1, slave.acceptCount());
        Thread.sleep(400);
        bus.writeNumeric(kept, 1, ModbusArea.HOLDING, 3, ModbusDataType.INT16, 8);
        assertEquals(2, slave.acceptCount());
    }

    @Test
    void connectFailureIsModbusException() {
        TcpModbusBus isolated = new TcpModbusBus(200, 200);
        ModbusChannel dead = new ModbusChannel("dead", "127.0.0.1", 1);
        isolated.retain(dead);
        assertThrows(ModbusException.class,
                () -> isolated.readNumeric(dead, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16));
        isolated.close();
    }

    /** 进程内 Modbus TCP 从站，仅覆盖本测试用到的功能码。 */
    private static final class LoopbackModbusTcpSlave implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService pool = Executors.newCachedThreadPool();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ConcurrentHashMap<String, Integer> holdings = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> coils = new ConcurrentHashMap<>();
        private final AtomicInteger accepts = new AtomicInteger();

        private LoopbackModbusTcpSlave(ServerSocket server) {
            this.server = server;
        }

        static LoopbackModbusTcpSlave start() throws IOException {
            LoopbackModbusTcpSlave slave = new LoopbackModbusTcpSlave(new ServerSocket(0));
            slave.pool.execute(slave::acceptLoop);
            return slave;
        }

        int port() {
            return server.getLocalPort();
        }

        int acceptCount() {
            return accepts.get();
        }

        private void acceptLoop() {
            while (running.get()) {
                try {
                    Socket socket = server.accept();
                    accepts.incrementAndGet();
                    pool.execute(() -> handle(socket));
                } catch (IOException ex) {
                    if (running.get()) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket;
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                while (running.get()) {
                    int tid = in.readUnsignedShort();
                    in.readUnsignedShort();
                    int length = in.readUnsignedShort();
                    int unitId = in.readUnsignedByte();
                    byte[] pdu = in.readNBytes(length - 1);
                    byte[] resp = dispatch(unitId, pdu);
                    out.writeShort(tid);
                    out.writeShort(0);
                    out.writeShort(1 + resp.length);
                    out.writeByte(unitId);
                    out.write(resp);
                    out.flush();
                }
            } catch (IOException ignored) {
                // 客户端断开
            }
        }

        private byte[] dispatch(int unitId, byte[] pdu) {
            int fc = pdu[0] & 0xFF;
            int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
            int quantity = pdu.length >= 5 ? ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF) : 1;
            return switch (fc) {
                case 0x01 -> coilRead(unitId, offset, quantity);
                case 0x03 -> holdingRead(unitId, offset, quantity);
                case 0x05 -> {
                    coils.put(key(unitId, offset), (pdu[3] & 0xFF) == 0xFF);
                    yield pdu;
                }
                case 0x06 -> {
                    int value = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
                    holdings.put(key(unitId, offset), value);
                    yield pdu;
                }
                default -> new byte[] { (byte) (fc | 0x80), 0x01 };
            };
        }

        private byte[] coilRead(int unitId, int offset, int quantity) {
            int count = Math.max(1, quantity);
            int bytes = (count + 7) / 8;
            byte[] resp = new byte[2 + bytes];
            resp[0] = 0x01;
            resp[1] = (byte) bytes;
            for (int i = 0; i < count; i++) {
                if (Boolean.TRUE.equals(coils.get(key(unitId, offset + i)))) {
                    resp[2 + i / 8] |= (byte) (1 << (i % 8));
                }
            }
            return resp;
        }

        private byte[] holdingRead(int unitId, int offset, int quantity) {
            int count = Math.max(1, quantity);
            byte[] resp = new byte[2 + count * 2];
            resp[0] = 0x03;
            resp[1] = (byte) (count * 2);
            for (int i = 0; i < count; i++) {
                int value = holdings.getOrDefault(key(unitId, offset + i), 0);
                resp[2 + i * 2] = (byte) (value >>> 8);
                resp[3 + i * 2] = (byte) value;
            }
            return resp;
        }

        private static String key(int unitId, int offset) {
            return unitId + ":" + offset;
        }

        @Override
        public void close() {
            running.set(false);
            try {
                server.close();
            } catch (IOException ignored) {
                // 关闭监听失败忽略
            }
            pool.shutdownNow();
            try {
                pool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
