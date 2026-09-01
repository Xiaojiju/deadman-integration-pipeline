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
 * @param deliveryHints  投递提示（如 mqtt.publishTopic），能力相关
 * @param deadlineAt     截止时刻，可空
 */
public record FunctionCommand(
        String requestId,
        String deviceId,
        String functionId,
        String capabilityType,
        Attributes arguments,
        Attributes deliveryHints,
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
        deliveryHints = deliveryHints == null ? Attributes.empty() : deliveryHints;
    }

    /** 快捷创建命令，不含 requestId 与 deadline。 */
    public static FunctionCommand of(String deviceId, String functionId, Map<String, ?> arguments) {
        return new FunctionCommand(null, deviceId, functionId, null, Attributes.from(arguments), Attributes.empty(), null);
    }

    public static FunctionCommand of(
            String deviceId,
            String functionId,
            Map<String, ?> arguments,
            Map<String, ?> deliveryHints) {
        return new FunctionCommand(
                null,
                deviceId,
                functionId,
                null,
                Attributes.from(arguments),
                Attributes.from(deliveryHints),
                null);
    }

    /** 兼容旧六参构造（无 deliveryHints）。 */
    public FunctionCommand(
            String requestId,
            String deviceId,
            String functionId,
            String capabilityType,
            Attributes arguments,
            Instant deadlineAt) {
        this(requestId, deviceId, functionId, capabilityType, arguments, Attributes.empty(), deadlineAt);
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
                Attributes.empty(),
                envelope.deadlineAt()
        );
    }
}
