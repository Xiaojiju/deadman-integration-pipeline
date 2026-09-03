package com.mtfm.gateway.capability.modbus.rtu;

import com.mtfm.gateway.capability.modbus.ModbusArea;
import com.mtfm.gateway.capability.modbus.ModbusChannel;
import com.mtfm.gateway.capability.modbus.ModbusDataType;
import com.mtfm.gateway.capability.modbus.ModbusPdu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtuModbusBusTest {

    private LoopbackSerialLink link;
    private LoopbackRtuSlave slave;
    private RtuModbusBus bus;
    private ModbusChannel channel;

    @BeforeEach
    void start() {
        link = new LoopbackSerialLink();
        slave = new LoopbackRtuSlave(link.slave());
        slave.start();
        bus = new RtuModbusBus(ignored -> link.master(), 1000);
        channel = ModbusChannel.rtu("c1", "COM-LOOP", 9600, 8, "NONE", 1);
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
    void twoUnitIdsShareSerialSession() {
        bus.writeNumeric(channel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 11);
        bus.writeNumeric(channel, 2, ModbusArea.HOLDING, 0, ModbusDataType.INT16, 22);
        assertEquals(11, bus.readNumeric(channel, 1, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(22, bus.readNumeric(channel, 2, ModbusArea.HOLDING, 0, ModbusDataType.INT16).intValue());
        assertEquals(1, bus.refCount(channel.sessionKey()));
        assertEquals("rtu:COM-LOOP", channel.sessionKey());
    }

    private static final class LoopbackSerialLink {
        private final BlockingQueue<Integer> toSlave = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> toMaster = new LinkedBlockingQueue<>();

        ModbusSerialPort master() {
            return new QueueSerialPort(toSlave, toMaster);
        }

        ModbusSerialPort slave() {
            return new QueueSerialPort(toMaster, toSlave);
        }
    }

    private static final class QueueSerialPort implements ModbusSerialPort {
        private final BlockingQueue<Integer> outbound;
        private final BlockingQueue<Integer> inbound;

        private QueueSerialPort(BlockingQueue<Integer> outbound, BlockingQueue<Integer> inbound) {
            this.outbound = outbound;
            this.inbound = inbound;
        }

        @Override
        public void write(byte[] frame) {
            for (byte item : frame) {
                outbound.add(item & 0xFF);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length, int timeoutMs) throws IOException {
            try {
                Integer first = inbound.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    return 0;
                }
                buffer[offset] = first.byteValue();
                int count = 1;
                while (count < length) {
                    Integer next = inbound.poll(1, TimeUnit.MILLISECONDS);
                    if (next == null) {
                        break;
                    }
                    buffer[offset + count] = next.byteValue();
                    count++;
                }
                return count;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("读串口回环被中断", ex);
            }
        }

        @Override
        public void close() {
            inbound.clear();
        }
    }

    private static final class LoopbackRtuSlave implements AutoCloseable {
        private final ModbusSerialPort port;
        private final ExecutorService pool = Executors.newSingleThreadExecutor();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ConcurrentHashMap<String, Integer> holdings = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> coils = new ConcurrentHashMap<>();

        private LoopbackRtuSlave(ModbusSerialPort port) {
            this.port = port;
        }

        void start() {
            pool.execute(this::loop);
        }

        private void loop() {
            while (running.get()) {
                try {
                    byte[] frame = readFrame();
                    if (frame.length < 4) {
                        continue;
                    }
                    int unitId = frame[0] & 0xFF;
                    byte[] pdu = ModbusPdu.unwrapRtu(frame, unitId);
                    port.write(ModbusPdu.wrapRtu(unitId, dispatch(unitId, pdu)));
                } catch (Exception ignored) {
                    if (!running.get()) {
                        return;
                    }
                }
            }
        }

        private byte[] readFrame() throws IOException {
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            while (running.get()) {
                int n = port.read(buf, 0, buf.length, 20);
                if (n > 0) {
                    collected.write(buf, 0, n);
                    continue;
                }
                if (collected.size() >= 4) {
                    return collected.toByteArray();
                }
            }
            return collected.toByteArray();
        }

        private byte[] dispatch(int unitId, byte[] pdu) {
            int fc = pdu[0] & 0xFF;
            int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
            int quantity = pdu.length >= 5 ? ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF) : 1;
            return switch (fc) {
                case 0x01 -> {
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
                    yield resp;
                }
                case 0x03 -> {
                    int count = Math.max(1, quantity);
                    byte[] resp = new byte[2 + count * 2];
                    resp[0] = 0x03;
                    resp[1] = (byte) (count * 2);
                    for (int i = 0; i < count; i++) {
                        int value = holdings.getOrDefault(key(unitId, offset + i), 0);
                        resp[2 + i * 2] = (byte) (value >>> 8);
                        resp[3 + i * 2] = (byte) value;
                    }
                    yield resp;
                }
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

        private static String key(int unitId, int offset) {
            return unitId + ":" + offset;
        }

        @Override
        public void close() {
            running.set(false);
            port.close();
            pool.shutdownNow();
        }
    }
}
