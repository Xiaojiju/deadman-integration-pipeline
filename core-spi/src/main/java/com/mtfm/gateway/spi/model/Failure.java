package com.mtfm.gateway.spi.model;

import java.util.Objects;

/**
 * 流水线失败描述。禁止携带连接句柄。
 *
 * <p>使用示例：
 * <pre>{@code
 * Failure failure = Failure.functionNotFound("dev-001", "fn.read");
 * Failure retryable = Failure.publisherError("cloud", "MQTT 断开", true);
 * draft.withError(Failure.pluginError("auth", "无权限"));
 * }</pre>
 *
 * @param code      稳定错误码，见 {@link FailureCodes}
 * @param message   可读说明
 * @param source    来源（插件名、能力类型或 core）
 * @param retryable 是否建议重试
 */
public record Failure(String code, String message, String source, boolean retryable) {

    public Failure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
    }

    public static Failure of(String code, String message, String source, boolean retryable) {
        return new Failure(code, message, source, retryable);
    }

    public static Failure pluginError(String source, String message) {
        return of(FailureCodes.PLUGIN_ERROR, message, source, false);
    }

    public static Failure timeout(String source, String message) {
        return of(FailureCodes.TIMEOUT, message, source, true);
    }

    public static Failure noDriver(String deviceId) {
        return of(FailureCodes.NO_DRIVER, "设备未绑定南向能力: " + deviceId, "core", false);
    }

    public static Failure executorError(String source, String message, boolean retryable) {
        return of(FailureCodes.EXECUTOR_ERROR, message, source, retryable);
    }

    public static Failure publisherError(String source, String message, boolean retryable) {
        return of(FailureCodes.PUBLISHER_ERROR, message, source, retryable);
    }

    public static Failure decodeError(String source, String message) {
        return of(FailureCodes.DECODE_ERROR, message, source, false);
    }

    public static Failure functionNotFound(String deviceId, String functionId) {
        return of(FailureCodes.FUNCTION_NOT_FOUND,
                "功能不存在: " + deviceId + "/" + functionId, "core", false);
    }

    public static Failure noExecutor(String capabilityType) {
        return of(FailureCodes.NO_EXECUTOR, "未注册执行器: " + capabilityType, "core", false);
    }

    public static Failure ingressOverflow() {
        return of(FailureCodes.INGRESS_OVERFLOW, "入站命令队列已满", "core", true);
    }

    public static Failure egressOverflow() {
        return of(FailureCodes.EGRESS_OVERFLOW, "出站回执队列已满", "core", true);
    }

    public static Failure deviceQueueOverflow(String deviceId) {
        return of(FailureCodes.DEVICE_QUEUE_OVERFLOW, "设备执行队列已满: " + deviceId, "core", true);
    }

    public static Failure deviceSlotExhausted(String deviceId) {
        return of(FailureCodes.DEVICE_SLOT_EXHAUSTED, "活跃设备槽已满: " + deviceId, "core", true);
    }

    public static Failure pipelineStopped() {
        return of(FailureCodes.PIPELINE_STOPPED, "流水线已停止", "core", false);
    }

    public static Failure accessDenied(String deviceId, String functionId) {
        return of(FailureCodes.ACCESS_DENIED,
                "功能无写权限: " + deviceId + "/" + functionId, "core", false);
    }

    public static Failure replyWaiterFull(String deviceId) {
        return of(FailureCodes.REPLY_WAITER_FULL, "应答等待槽已满: " + deviceId, "core", true);
    }
}
