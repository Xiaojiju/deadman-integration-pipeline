package com.mtfm.gateway.spi.model;

import java.util.Map;

/**
 * {@link com.mtfm.gateway.spi.capability.FunctionExecutor} 同步返回值。执行器不得自行 publish。
 *
 * <p>使用示例：
 * <pre>{@code
 * return ExecutionResult.success(requestId, deviceId, functionId, Map.of("value", 42));
 * return ExecutionResult.failed(command, Failure.executorError("modbus", "超时", true));
 * }</pre>
 *
 * @param requestId  请求 ID
 * @param deviceId   设备 ID
 * @param functionId 功能 ID
 * @param status     执行状态
 * @param data       成功时的返回数据
 * @param failure    失败时的错误描述
 */
public record ExecutionResult(
        String requestId,
        String deviceId,
        String functionId,
        ExecutionStatus status,
        Attributes data,
        Failure failure
) {

    public ExecutionResult {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        data = data == null ? Attributes.empty() : data;
        if (terminalOk(status) && failure != null) {
            throw new IllegalArgumentException(status + " 不得携带 failure");
        }
        if (!terminalOk(status) && failure == null) {
            throw new IllegalArgumentException(status + " 必须携带 failure");
        }
    }

    /** 创建成功结果。 */
    public static ExecutionResult success(String requestId, String deviceId, String functionId, Attributes data) {
        return new ExecutionResult(requestId, deviceId, functionId, ExecutionStatus.SUCCESS, data, null);
    }

    public static ExecutionResult success(String requestId, String deviceId, String functionId, Map<String, ?> data) {
        return success(requestId, deviceId, functionId, Attributes.from(data));
    }

    /** 南向已送出、等待设备应答。 */
    public static ExecutionResult accepted(String requestId, String deviceId, String functionId, Map<String, ?> data) {
        return new ExecutionResult(requestId, deviceId, functionId, ExecutionStatus.ACCEPTED, Attributes.from(data), null);
    }

    /** 创建失败结果。 */
    public static ExecutionResult failed(FunctionCommand command, Failure failure) {
        return new ExecutionResult(command.requestId(), command.deviceId(), command.functionId(),
                ExecutionStatus.FAILED, Attributes.empty(), failure);
    }

    /** 创建失败结果并携带投影后的回包数据。 */
    public static ExecutionResult failed(FunctionCommand command, Failure failure, Map<String, ?> data) {
        return new ExecutionResult(command.requestId(), command.deviceId(), command.functionId(),
                ExecutionStatus.FAILED, Attributes.from(data), failure);
    }

    /** 创建超时结果。 */
    public static ExecutionResult timeout(FunctionCommand command, Failure failure) {
        return new ExecutionResult(command.requestId(), command.deviceId(), command.functionId(),
                ExecutionStatus.TIMEOUT, Attributes.empty(), failure);
    }

    /** 创建拒绝结果（来自命令）。 */
    public static ExecutionResult rejected(FunctionCommand command, Failure failure) {
        return new ExecutionResult(command.requestId(), command.deviceId(), command.functionId(),
                ExecutionStatus.REJECTED, Attributes.empty(), failure);
    }

    /** 创建拒绝结果（来自信封）。 */
    public static ExecutionResult rejected(Envelope envelope, Failure failure) {
        return new ExecutionResult(envelope.requestId(), envelope.deviceId(), envelope.functionId(),
                ExecutionStatus.REJECTED, Attributes.empty(), failure);
    }

    public static boolean terminalOk(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.ACCEPTED;
    }
}
