package com.mtfm.gateway.spi.model;

/**
 * 稳定失败码常量。命令路径失败即停后由 Correlate 嵌进回执。
 *
 * <p>各常量与 {@link Failure} 工厂方法一一对应，供宿主与监控统一识别。
 */
public final class FailureCodes {

    /** 插件业务拒绝。 */
    public static final String PLUGIN_REJECT = "PLUGIN_REJECT";
    /** 插件内部错误。 */
    public static final String PLUGIN_ERROR = "PLUGIN_ERROR";
    /** 执行器错误。 */
    public static final String EXECUTOR_ERROR = "EXECUTOR_ERROR";
    /** 执行超时。 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 设备未绑定南向驱动。 */
    public static final String NO_DRIVER = "NO_DRIVER";
    /** 发布器错误。 */
    public static final String PUBLISHER_ERROR = "PUBLISHER_ERROR";
    /** 解码错误。 */
    public static final String DECODE_ERROR = "DECODE_ERROR";
    /** 重复设备绑定。 */
    public static final String DUPLICATE_BINDING = "DUPLICATE_BINDING";
    /** 重复通道注册。 */
    public static final String DUPLICATE_CHANNEL = "DUPLICATE_CHANNEL";
    /** 功能不存在。 */
    public static final String FUNCTION_NOT_FOUND = "FUNCTION_NOT_FOUND";
    /** 未注册执行器。 */
    public static final String NO_EXECUTOR = "NO_EXECUTOR";
    /** 出站 channelHint 被篡改。 */
    public static final String CHANNEL_HINT_CHANGED = "CHANNEL_HINT_CHANGED";
    /** 通道不匹配。 */
    public static final String CHANNEL_MISMATCH = "CHANNEL_MISMATCH";
    /** 入站队列溢出。 */
    public static final String INGRESS_OVERFLOW = "INGRESS_OVERFLOW";
    /** 出站队列溢出。 */
    public static final String EGRESS_OVERFLOW = "EGRESS_OVERFLOW";
    /** 设备执行队列溢出。 */
    public static final String DEVICE_QUEUE_OVERFLOW = "DEVICE_QUEUE_OVERFLOW";
    /** 活跃设备槽耗尽。 */
    public static final String DEVICE_SLOT_EXHAUSTED = "DEVICE_SLOT_EXHAUSTED";
    /** 流水线已停止。 */
    public static final String PIPELINE_STOPPED = "PIPELINE_STOPPED";
    /** 功能无写权限。 */
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    /** 应答等待槽已满。 */
    public static final String REPLY_WAITER_FULL = "REPLY_WAITER_FULL";

    private FailureCodes() {
    }
}
