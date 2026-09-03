package com.mtfm.gateway.spi.model;

/**
 * 命令回执体。Correlate 阶段直接组装为 {@link OutboundDraft}。
 *
 * <p>使用示例：
 * <pre>{@code
 * CommandResponse response = CommandResponse.from(executionResult);
 * OutboundDraft draft = OutboundDraft.builder()
 *         .channelHint(Channels.CLOUD).body(response).build();
 * }</pre>
 *
 * @param requestId  请求 ID
 * @param deviceId   设备 ID
 * @param functionId 功能 ID
 * @param status     执行状态
 * @param data       成功时的返回数据
 * @param error      失败时的错误描述
 */
public record CommandResponse(
        String requestId,
        String deviceId,
        String functionId,
        ExecutionStatus status,
        Attributes data,
        Failure error
) implements OutboundBody {

    public CommandResponse {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        data = data == null ? Attributes.empty() : data;
        if (ExecutionResult.terminalOk(status) && error != null) {
            throw new IllegalArgumentException(status + " 不得携带 error");
        }
        if (!ExecutionResult.terminalOk(status) && error == null) {
            throw new IllegalArgumentException(status + " 必须携带 error");
        }
    }

    @Override
    public EnvelopeKind kind() {
        return EnvelopeKind.RESPONSE;
    }

    public CommandResponse withData(Attributes newData) {
        return new CommandResponse(requestId, deviceId, functionId, status, newData, error);
    }

    /** 从 {@link ExecutionResult} 转换回执体。 */
    public static CommandResponse from(ExecutionResult result) {
        return new CommandResponse(
                result.requestId(),
                result.deviceId(),
                result.functionId(),
                result.status(),
                result.data(),
                result.failure()
        );
    }
}
