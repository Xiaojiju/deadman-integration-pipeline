package com.mtfm.gateway.spi.model;

import java.time.Instant;
import java.util.List;

/**
 * 已贴胶入站信封。不可修改，不对外提供 mutator。
 *
 * <p>调度核心与 {@link com.mtfm.gateway.spi.capability.FunctionExecutor} 只读；
 * 由 {@link com.mtfm.gateway.spi.port.EnvelopeSealer#seal(EnvelopeDraft)} 从草稿生成。
 *
 * <p>使用示例：
 * <pre>{@code
 * Envelope envelope = sealer.seal(draft);
 * FunctionCommand command = FunctionCommand.from(envelope);
 * }</pre>
 *
 * @param envelopeId     全局唯一信封 ID，贴胶时分配
 * @param requestId      上游请求 ID
 * @param direction      方向
 * @param kind           信封种类
 * @param deviceId       设备 ID
 * @param functionId     功能 ID
 * @param capabilityType 南向能力类型
 * @param payload        命令参数或遥测点
 * @param headers        传输头
 * @param trace          插件/阶段轨迹
 * @param error          前置失败
 * @param createdAt      创建时间
 * @param deadlineAt     截止时刻
 */
public record Envelope(
        String envelopeId,
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

    public Envelope {
        if (envelopeId == null || envelopeId.isBlank()) {
            throw new IllegalArgumentException("envelopeId 不能为空");
        }
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

    /** 是否已携带前置失败。 */
    public boolean hasError() {
        return error != null;
    }
}
