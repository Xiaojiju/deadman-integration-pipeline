package com.mtfm.gateway.capability.hikvision;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存桩客户端，仅用于单测，不发起真实 HTTP 请求。
 */
public final class StubHikvisionClient implements HikvisionClient {

    /**
     * 通道 ID
     */
    private final String channelId;
    /**
     * 远程控门计数
     */
    private final AtomicInteger remoteControls = new AtomicInteger();
    /**
     * 是否关闭
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /**
     * 用户操作计数
     */
    private final ConcurrentHashMap<String, AtomicInteger> userOps = new ConcurrentHashMap<>();
    /**
     * 卡片操作计数
     */
    private final ConcurrentHashMap<String, AtomicInteger> cardOps = new ConcurrentHashMap<>();

    /**
     * 创建 Stub 客户端
     * 
     * @param channelId 通道 ID
     */
    public StubHikvisionClient(String channelId) {
        this.channelId = channelId;
    }

    /**
     * 创建 Stub 客户端
     * 
     * @param config 通道配置
     */
    public StubHikvisionClient(HikvisionChannelConfig config) {
        this(config.channelId());
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public boolean remoteControlDoor(String deviceSerialNo, String target, String command) {
        ensureOpen();
        remoteControls.incrementAndGet();
        return true;
    }

    @Override
    public boolean setUpUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr) {
        ensureOpen();
        userOps.computeIfAbsent("setUpUser", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public boolean modifyUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr) {
        ensureOpen();
        userOps.computeIfAbsent("modifyUser", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public boolean deleteUser(String deviceSerialNo, List<String> employeeNoList) {
        ensureOpen();
        userOps.computeIfAbsent("deleteUser", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public boolean setUpCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType) {
        ensureOpen();
        cardOps.computeIfAbsent("setUpCard", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public boolean modifyCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType) {
        ensureOpen();
        cardOps.computeIfAbsent("modifyCard", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public boolean deleteCard(String deviceSerialNo, List<String> cardNoList) {
        ensureOpen();
        cardOps.computeIfAbsent("deleteCard", key -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    @Override
    public void close() {
        closed.set(true);
    }

    public boolean closed() {
        return closed.get();
    }

    public int remoteControls() {
        return remoteControls.get();
    }

    public int userOpCount(String op) {
        AtomicInteger count = userOps.get(op);
        return count == null ? 0 : count.get();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("客户端已关闭: " + channelId);
        }
    }
}
