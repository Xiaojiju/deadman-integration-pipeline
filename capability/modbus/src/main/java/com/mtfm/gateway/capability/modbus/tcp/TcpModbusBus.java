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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实 Modbus TCP 总线：同一 host:port 复用一条 TCP，按 unitId 区分从站。
 *
 * <p>retain/release 引用计数，计数归零关闭连接。读写失败时断线并重试一次。
 */
public final class TcpModbusBus extends AbstractModbusBus {

    private static final Logger LOG = LoggerFactory.getLogger(TcpModbusBus.class);

    static final long DEFAULT_IDLE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final long idleTimeoutMs;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService idleWatch;

    public TcpModbusBus() {
        this(3000, 3000);
    }

    public TcpModbusBus(int connectTimeoutMs, int requestTimeoutMs) {
        this(connectTimeoutMs, requestTimeoutMs, DEFAULT_IDLE_TIMEOUT_MS);
    }

    public TcpModbusBus(int connectTimeoutMs, int requestTimeoutMs, long idleTimeoutMs) {
        this.connectTimeoutMs = Math.max(200, connectTimeoutMs);
        this.requestTimeoutMs = Math.max(200, requestTimeoutMs);
        this.idleTimeoutMs = Math.max(50L, idleTimeoutMs);
        this.idleWatch = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modbus-tcp-idle");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.min(30_000L, this.idleTimeoutMs);
        this.idleWatch.scheduleAtFixedRate(this::closeIdleSockets, period, period, TimeUnit.MILLISECONDS);
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
        idleWatch.shutdownNow();
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
                byte[] response = session.transact(unitId, pdu);
                if (!channel.keepAlive()) {
                    session.closeQuietly();
                }
                return response;
            } catch (IOException first) {
                session.closeQuietly();
                try {
                    byte[] response = session.transact(unitId, pdu);
                    if (!channel.keepAlive()) {
                        session.closeQuietly();
                    }
                    return response;
                } catch (IOException retry) {
                    session.closeQuietly();
                    throw new ModbusException(
                            "Modbus TCP 失败 " + channel.sessionKey() + " unitId=" + unitId + ": " + retry.getMessage(),
                            retry);
                }
            }
        }
    }

    private void closeIdleSockets() {
        long now = System.currentTimeMillis();
        for (Session session : sessions.values()) {
            synchronized (session) {
                session.closeIfIdle(now, idleTimeoutMs);
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
        private long lastUsedMs;

        private Session(ModbusChannel channel) {
            this.channel = channel;
        }

        private byte[] transact(int unitId, byte[] pdu) throws IOException {
            ensureConnected();
            lastUsedMs = System.currentTimeMillis();
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
            lastUsedMs = System.currentTimeMillis();
            return respPdu;
        }

        private void ensureConnected() throws IOException {
            long now = System.currentTimeMillis();
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                if (!channel.keepAlive() || lastUsedMs <= 0 || now - lastUsedMs <= idleTimeoutMs) {
                    return;
                }
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
            lastUsedMs = System.currentTimeMillis();
            LOG.info("已连接 Modbus TCP {}", channel.sessionKey());
        }

        private void closeIfIdle(long now, long timeoutMs) {
            if (!channel.keepAlive() || socket == null || lastUsedMs <= 0) {
                return;
            }
            if (now - lastUsedMs > timeoutMs) {
                LOG.info("Modbus TCP 空闲超时，关闭 {}", channel.sessionKey());
                closeQuietly();
            }
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
