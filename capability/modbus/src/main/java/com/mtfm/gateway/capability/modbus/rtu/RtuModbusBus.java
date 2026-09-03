package com.mtfm.gateway.capability.modbus.rtu;

import com.mtfm.gateway.capability.modbus.AbstractModbusBus;
import com.mtfm.gateway.capability.modbus.ModbusChannel;
import com.mtfm.gateway.capability.modbus.ModbusException;
import com.mtfm.gateway.capability.modbus.ModbusPdu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实 Modbus RTU 总线：同一串口独占复用，按 unitId 区分从站。
 */
public final class RtuModbusBus extends AbstractModbusBus {

    private static final Logger LOG = LoggerFactory.getLogger(RtuModbusBus.class);

    private final ModbusSerialPorts ports;
    private final int requestTimeoutMs;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public RtuModbusBus() {
        this(3000);
    }

    public RtuModbusBus(int requestTimeoutMs) {
        this(new JSerialCommPorts(), requestTimeoutMs);
    }

    public RtuModbusBus(ModbusSerialPorts ports, int requestTimeoutMs) {
        if (ports == null) {
            throw new IllegalArgumentException("ports 不能为空");
        }
        this.ports = ports;
        this.requestTimeoutMs = Math.max(200, requestTimeoutMs);
    }

    @Override
    public void retain(ModbusChannel channel) {
        Session session = sessions.computeIfAbsent(channel.sessionKey(), key -> new Session(channel));
        session.refs.incrementAndGet();
    }

    @Override
    public void release(ModbusChannel channel) {
        Session session = sessions.get(channel.sessionKey());
        if (session == null) {
            return;
        }
        if (session.refs.decrementAndGet() <= 0) {
            sessions.remove(channel.sessionKey(), session);
            session.closeQuietly();
        }
    }

    @Override
    public int refCount(String sessionKey) {
        Session session = sessions.get(sessionKey);
        return session == null ? 0 : Math.max(0, session.refs.get());
    }

    @Override
    public void close() {
        for (Session session : sessions.values()) {
            session.closeQuietly();
        }
        sessions.clear();
    }

    @Override
    protected byte[] transact(ModbusChannel channel, int unitId, byte[] pdu) {
        if (!channel.rtu()) {
            throw new ModbusException("RTU 总线不能处理 TCP 通道: " + channel.sessionKey());
        }
        Session session = sessions.computeIfAbsent(channel.sessionKey(), key -> new Session(channel));
        synchronized (session) {
            try {
                return session.transact(unitId, pdu);
            } catch (IOException first) {
                session.closeQuietly();
                try {
                    return session.transact(unitId, pdu);
                } catch (IOException retry) {
                    session.closeQuietly();
                    throw new ModbusException(
                            "Modbus RTU 失败 " + channel.sessionKey() + " unitId=" + unitId + ": " + retry.getMessage(),
                            retry);
                }
            }
        }
    }

    private final class Session {
        private final ModbusChannel channel;
        private final AtomicInteger refs = new AtomicInteger();
        private ModbusSerialPort port;

        private Session(ModbusChannel channel) {
            this.channel = channel;
        }

        private byte[] transact(int unitId, byte[] pdu) throws IOException {
            ensureOpen();
            silenceGap();
            port.write(ModbusPdu.wrapRtu(unitId, pdu));
            byte[] frame = readFrame();
            return ModbusPdu.unwrapRtu(frame, unitId);
        }

        private void ensureOpen() throws IOException {
            if (port != null) {
                return;
            }
            port = ports.open(channel);
            LOG.info("已打开 Modbus RTU {}", channel.sessionKey());
        }

        private void silenceGap() {
            try {
                Thread.sleep(charTimes(3.5));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ModbusException("RTU 帧间隔被中断");
            }
        }

        private byte[] readFrame() throws IOException {
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            long deadline = System.currentTimeMillis() + requestTimeoutMs;
            int idleMs = Math.max(2, charTimes(1.5));
            byte[] buf = new byte[256];
            while (System.currentTimeMillis() < deadline) {
                int n = port.read(buf, 0, buf.length, idleMs);
                if (n > 0) {
                    collected.write(buf, 0, n);
                    continue;
                }
                if (collected.size() >= 4) {
                    return collected.toByteArray();
                }
            }
            if (collected.size() >= 4) {
                return collected.toByteArray();
            }
            throw new IOException("RTU 读超时，已收 " + collected.size() + " 字节");
        }

        private int charTimes(double chars) {
            int baud = Math.max(1200, channel.baudRate());
            return Math.max(2, (int) Math.ceil(11.0 * chars * 1000.0 / baud));
        }

        private void closeQuietly() {
            if (port == null) {
                return;
            }
            port.close();
            port = null;
        }
    }
}
