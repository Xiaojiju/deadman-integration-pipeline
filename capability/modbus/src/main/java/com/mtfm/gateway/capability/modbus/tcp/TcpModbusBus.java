package com.mtfm.gateway.capability.modbus.tcp;

import com.mtfm.gateway.capability.modbus.AbstractModbusBus;
import com.mtfm.gateway.capability.modbus.ModbusChannel;
import com.mtfm.gateway.capability.modbus.ModbusException;
import com.mtfm.gateway.capability.modbus.ModbusPdu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实 Modbus TCP 总线：同一 host:port 复用一条 TCP，按 unitId 区分从站。
 *
 * <p>retain/release 引用计数，计数归零关闭连接。读写失败时断线并重试一次。
 */
public final class TcpModbusBus extends AbstractModbusBus {

    private static final Logger LOG = LoggerFactory.getLogger(TcpModbusBus.class);

    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public TcpModbusBus() {
        this(3000, 3000);
    }

    public TcpModbusBus(int connectTimeoutMs, int requestTimeoutMs) {
        this.connectTimeoutMs = Math.max(200, connectTimeoutMs);
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
        if (channel.rtu()) {
            throw new ModbusException("TCP 总线不能处理 RTU 通道: " + channel.sessionKey());
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
                            "Modbus TCP 失败 " + channel.sessionKey() + " unitId=" + unitId + ": " + retry.getMessage(),
                            retry);
                }
            }
        }
    }

    private final class Session {
        private final ModbusChannel channel;
        private final AtomicInteger refs = new AtomicInteger();
        private final AtomicInteger transactionId = new AtomicInteger();
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;

        private Session(ModbusChannel channel) {
            this.channel = channel;
        }

        private byte[] transact(int unitId, byte[] pdu) throws IOException {
            ensureConnected();
            int tid = transactionId.incrementAndGet() & 0xFFFF;
            int length = 1 + pdu.length;
            out.writeShort(tid);
            out.writeShort(0);
            out.writeShort(length);
            out.writeByte(unitId);
            out.write(pdu);
            out.flush();

            int respTid = in.readUnsignedShort();
            int protocol = in.readUnsignedShort();
            int respLen = in.readUnsignedShort();
            if (respTid != tid) {
                throw new IOException("事务号不匹配 expect=" + tid + " actual=" + respTid);
            }
            if (protocol != 0) {
                throw new IOException("协议标识无效: " + protocol);
            }
            if (respLen < 2) {
                throw new IOException("MBAP 长度过短: " + respLen);
            }
            int respUnit = in.readUnsignedByte();
            byte[] respPdu = in.readNBytes(respLen - 1);
            if (respPdu.length != respLen - 1) {
                throw new IOException("PDU 不完整");
            }
            if (respUnit != (unitId & 0xFF)) {
                throw new IOException("从站号不匹配 expect=" + unitId + " actual=" + respUnit);
            }
            ModbusPdu.requireSuccess(respPdu);
            return respPdu;
        }

        private void ensureConnected() throws IOException {
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                return;
            }
            closeQuietly();
            Socket next = new Socket();
            next.setTcpNoDelay(true);
            next.setKeepAlive(true);
            next.connect(new InetSocketAddress(channel.host(), channel.port()), connectTimeoutMs);
            next.setSoTimeout(requestTimeoutMs);
            socket = next;
            in = new DataInputStream(next.getInputStream());
            out = new DataOutputStream(next.getOutputStream());
            LOG.info("已连接 Modbus TCP {}", channel.sessionKey());
        }

        private void closeQuietly() {
            if (socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败忽略
            }
            socket = null;
            in = null;
            out = null;
        }
    }
}
