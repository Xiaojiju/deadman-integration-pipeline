package com.mtfm.gateway.spi.model;

/**
 * 已贴胶出站消息。不可修改，{@link com.mtfm.gateway.spi.capability.Publisher} 只读。
 *
 * <p>由 {@link com.mtfm.gateway.spi.port.EnvelopeSealer#seal(OutboundDraft)} 生成；
 * 重试时核心通过 {@link #withAttempt(int)} 递增次数，不改变业务体与通道提示。
 *
 * <p>使用示例：
 * <pre>{@code
 * OutboundMessage message = sealer.seal(outboundDraft);
 * PublishResult result = publisher.publish(message);
 * }</pre>
 *
 * @param messageId    全局唯一消息 ID
 * @param channelHint  北向通道提示
 * @param body         出站业务体
 * @param headers      传输头
 * @param priority     队列优先级
 * @param attempt      当前重试次数
 */
public record OutboundMessage(
        String messageId,
        String channelHint,
        OutboundBody body,
        MessageHeaders headers,
        MessagePriority priority,
        int attempt
) {

    public OutboundMessage {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        if (channelHint == null || channelHint.isBlank()) {
            throw new IllegalArgumentException("channelHint 不能为空");
        }
        if (body == null) {
            throw new IllegalArgumentException("body 不能为空");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority 不能为空");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt 不能为负");
        }
        headers = headers == null ? MessageHeaders.empty() : headers;
    }

    /** 从 body 推导信封种类。 */
    public EnvelopeKind kind() {
        return body.kind();
    }

    public String deviceId() {
        return body.deviceId();
    }

    public String requestId() {
        return body.requestId();
    }

    /**
     * 仅核心 Egress 递增重试次数；不改变业务体与通道提示。
     */
    public OutboundMessage withAttempt(int newAttempt) {
        return new OutboundMessage(messageId, channelHint, body, headers, priority, newAttempt);
    }
}
