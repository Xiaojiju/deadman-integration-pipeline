package com.mtfm.gateway.spi.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 未贴胶入站草稿。插件链只改本对象；贴胶后成为只读 {@link Envelope}。
 *
 * <p>草稿阶段允许 {@linkplain #withPayload(Attributes) 替换载荷}、
 * {@linkplain #appendTrace(String, String) 追加轨迹} 等不可变拷贝操作；
 * 调度核心在 Normalize 阶段末调用 {@link com.mtfm.gateway.spi.port.EnvelopeSealer#seal(EnvelopeDraft)} 贴胶。
 *
 * <p>使用示例：
 * <pre>{@code
 * EnvelopeDraft draft = EnvelopeDraft.builder()
 *         .deviceId("dev-001")
 *         .kind(EnvelopeKind.COMMAND)
 *         .functionId("fn.read")
 *         .payload(Map.of("offset", 0))
 *         .build()
 *         .appendTrace("decode", "modbus");
 *
 * EnvelopeDraft enriched = draft.withPayload(draft.payload().with("offset", 10));
 * }</pre>
 *
 * @param requestId      上游请求 ID，可空
 * @param direction      方向，默认 INBOUND
 * @param kind           信封种类
 * @param deviceId       设备 ID
 * @param functionId     功能 ID；{@link EnvelopeKind#COMMAND} 必填
 * @param capabilityType 南向能力类型提示
 * @param payload        命令参数或遥测点
 * @param headers        传输头
 * @param trace          插件/阶段轨迹
 * @param error          前置失败，有则不再 Execute
 * @param createdAt      创建时间
 * @param deadlineAt     截止时刻，可空
 */
public record EnvelopeDraft(
        String requestId,
        Direction direction,
        EnvelopeKind kind,
        String deviceId,
        String functionId,
        String capabilityType,
        Attributes payload,
        MessageHeaders headers,
        List<TraceEntry> trace,
        Failure error,
        Instant createdAt,
        Instant deadlineAt
) {

    public EnvelopeDraft {
        if (direction == null) {
            throw new IllegalArgumentException("direction 不能为空");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为空");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为空");
        }
        payload = payload == null ? Attributes.empty() : payload;
        headers = headers == null ? MessageHeaders.empty() : headers;
        trace = trace == null || trace.isEmpty() ? List.of() : List.copyOf(trace);
        if (kind == EnvelopeKind.COMMAND && (functionId == null || functionId.isBlank())) {
            throw new IllegalArgumentException("COMMAND 必须包含 functionId");
        }
    }

    /** 返回替换载荷后的新草稿。 */
    public EnvelopeDraft withPayload(Attributes newPayload) {
        return new EnvelopeDraft(requestId, direction, kind, deviceId, functionId, capabilityType,
                newPayload, headers, trace, error, createdAt, deadlineAt);
    }

    /** 返回替换传输头后的新草稿。 */
    public EnvelopeDraft withHeaders(MessageHeaders newHeaders) {
        return new EnvelopeDraft(requestId, direction, kind, deviceId, functionId, capabilityType,
                payload, newHeaders, trace, error, createdAt, deadlineAt);
    }

    /** 返回替换功能 ID 后的新草稿。 */
    public EnvelopeDraft withFunctionId(String newFunctionId) {
        return new EnvelopeDraft(requestId, direction, kind, deviceId, newFunctionId, capabilityType,
                payload, headers, trace, error, createdAt, deadlineAt);
    }

    /** 返回替换能力类型后的新草稿。 */
    public EnvelopeDraft withCapabilityType(String newCapabilityType) {
        return new EnvelopeDraft(requestId, direction, kind, deviceId, functionId, newCapabilityType,
                payload, headers, trace, error, createdAt, deadlineAt);
    }

    /** 返回携带失败信息的新草稿；失败即停，不再进入 Execute。 */
    public EnvelopeDraft withError(Failure newError) {
        return new EnvelopeDraft(requestId, direction, kind, deviceId, functionId, capabilityType,
                payload, headers, trace, newError, createdAt, deadlineAt);
    }

    /** 追加一条插件/阶段轨迹记录。 */
    public EnvelopeDraft appendTrace(String name, String note) {
        List<TraceEntry> next = new ArrayList<>(trace);
        next.add(TraceEntry.of(name, note));
        return new EnvelopeDraft(requestId, direction, kind, deviceId, functionId, capabilityType,
                payload, headers, next, error, createdAt, deadlineAt);
    }

    /** 是否已携带前置失败。 */
    public boolean hasError() {
        return error != null;
    }

    /** 创建草稿构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String requestId;
        private Direction direction = Direction.INBOUND;
        private EnvelopeKind kind = EnvelopeKind.COMMAND;
        private String deviceId;
        private String functionId;
        private String capabilityType;
        private Attributes payload = Attributes.empty();
        private MessageHeaders headers = MessageHeaders.empty();
        private List<TraceEntry> trace = List.of();
        private Failure error;
        private Instant createdAt;
        private Instant deadlineAt;

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder direction(Direction direction) {
            this.direction = direction;
            return this;
        }

        public Builder kind(EnvelopeKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder functionId(String functionId) {
            this.functionId = functionId;
            return this;
        }

        public Builder capabilityType(String capabilityType) {
            this.capabilityType = capabilityType;
            return this;
        }

        public Builder payload(Attributes payload) {
            this.payload = payload;
            return this;
        }

        public Builder payload(Map<String, ?> payload) {
            this.payload = Attributes.from(payload);
            return this;
        }

        public Builder headers(MessageHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = MessageHeaders.from(headers);
            return this;
        }

        public Builder trace(List<TraceEntry> trace) {
            this.trace = trace;
            return this;
        }

        public Builder error(Failure error) {
            this.error = error;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder deadlineAt(Instant deadlineAt) {
            this.deadlineAt = deadlineAt;
            return this;
        }

        public EnvelopeDraft build() {
            return new EnvelopeDraft(
                    requestId,
                    direction,
                    kind,
                    deviceId,
                    functionId,
                    capabilityType,
                    payload,
                    headers,
                    trace,
                    error,
                    createdAt == null ? Instant.now() : createdAt,
                    deadlineAt
            );
        }
    }
}
