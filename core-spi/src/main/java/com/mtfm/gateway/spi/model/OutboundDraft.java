package com.mtfm.gateway.spi.model;

/**
 * 未贴胶出站草稿。插件可改 body / headers，禁止改 {@code channelHint}。
 *
 * <p>Correlate 阶段组装草稿，出站插件链处理后由
 * {@link com.mtfm.gateway.spi.port.EnvelopeSealer#seal(OutboundDraft)} 贴胶为 {@link OutboundMessage}。
 *
 * <p>使用示例：
 * <pre>{@code
 * OutboundDraft draft = OutboundDraft.builder()
 *         .channelHint(Channels.CLOUD)
 *         .body(CommandResponse.from(executionResult))
 *         .priority(MessagePriority.HIGH)
 *         .build();
 *
 * OutboundDraft enriched = draft.withHeaders(draft.headers().with("trace-id", "abc"));
 * }</pre>
 *
 * @param messageId    消息 ID，贴胶前可空
 * @param channelHint  北向通道提示，插件不可改
 * @param body         出站业务体
 * @param headers      传输头
 * @param priority     队列优先级
 * @param attempt      当前重试次数
 */
public record OutboundDraft(
        String messageId,
        String channelHint,
        OutboundBody body,
        MessageHeaders headers,
        MessagePriority priority,
        int attempt
) {

    public OutboundDraft {
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

    /** 从 body 取设备 ID。 */
    public String deviceId() {
        return body.deviceId();
    }

    /** 从 body 取请求 ID。 */
    public String requestId() {
        return body.requestId();
    }

    /** 返回替换业务体后的新草稿。 */
    public OutboundDraft withBody(OutboundBody newBody) {
        return new OutboundDraft(messageId, channelHint, newBody, headers, priority, attempt);
    }

    /** 返回替换传输头后的新草稿。 */
    public OutboundDraft withHeaders(MessageHeaders newHeaders) {
        return new OutboundDraft(messageId, channelHint, body, newHeaders, priority, attempt);
    }

    /** 创建出站草稿构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageId;
        private String channelHint = Channels.CLOUD;
        private OutboundBody body;
        private MessageHeaders headers = MessageHeaders.empty();
        private MessagePriority priority;
        private int attempt;

        private Builder() {
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder channelHint(String channelHint) {
            this.channelHint = channelHint;
            return this;
        }

        public Builder body(OutboundBody body) {
            this.body = body;
            return this;
        }

        public Builder headers(MessageHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder priority(MessagePriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder attempt(int attempt) {
            this.attempt = attempt;
            return this;
        }

        public OutboundDraft build() {
            MessagePriority resolved = priority != null
                    ? priority
                    : (body instanceof CommandResponse ? MessagePriority.HIGH : MessagePriority.NORMAL);
            return new OutboundDraft(messageId, channelHint, body, headers, resolved, attempt);
        }
    }
}
