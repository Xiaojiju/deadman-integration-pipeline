package com.mtfm.gateway.spi.model;

import java.time.Instant;
import java.util.Map;

/**
 * Execute 阶段作业。由已贴胶 {@link EnvelopeKind#COMMAND} 信封物化。
 *
 * <p>使用示例：
 * <pre>{@code
 * FunctionCommand cmd = FunctionCommand.of("dev-001", "fn.read", Map.of("offset", 0));
 * FunctionCommand fromEnvelope = FunctionCommand.from(sealedEnvelope);
 * }</pre>
 *
 * @param requestId      上游请求 ID
 * @param deviceId       设备 ID
 * @param functionId     功能 ID
 * @param capabilityType 南向能力类型，可空
 * @param arguments      命令参数
 * @param deadlineAt     截止时刻，可空
 */
public record FunctionCommand(
        String requestId,
        String deviceId,
        String functionId,
        String capabilityType,
        Attributes arguments,
        Instant deadlineAt
) {

    public FunctionCommand {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (functionId == null || functionId.isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        arguments = arguments == null ? Attributes.empty() : arguments;
    }

    /** 快捷创建命令，不含 requestId 与 deadline。 */
    public static FunctionCommand of(String deviceId, String functionId, Map<String, ?> arguments) {
        return new FunctionCommand(null, deviceId, functionId, null, Attributes.from(arguments), null);
    }

    /** 从已贴胶 COMMAND 信封物化命令。 */
    public static FunctionCommand from(Envelope envelope) {
        if (envelope.kind() != EnvelopeKind.COMMAND) {
            throw new IllegalArgumentException("仅 COMMAND 可物化为 FunctionCommand");
        }
        return new FunctionCommand(
                envelope.requestId(),
                envelope.deviceId(),
                envelope.functionId(),
                envelope.capabilityType(),
                envelope.payload(),
                envelope.deadlineAt()
        );
    }
}
