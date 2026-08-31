package com.mtfm.gateway.spi.metrics;

import com.mtfm.gateway.spi.model.EnvelopeKind;
import com.mtfm.gateway.spi.model.ExecutionStatus;
import com.mtfm.gateway.spi.model.MessagePriority;

/**
 * 流水线指标回调端口。核心只打点，不绑定 Micrometer；宿主可实现并注入。
 */
public interface GatewayMetrics {

    /** 遥测入站丢弃原因。 */
    String DROP_TELEMETRY_INGRESS = "telemetry_ingress";
    /** 遥测出站丢弃原因。 */
    String DROP_TELEMETRY_EGRESS = "telemetry_egress";
    /** 出站队列溢出丢弃。 */
    String DROP_EGRESS_OVERFLOW = "egress_overflow";
    /** 出站插件丢弃。 */
    String DROP_OUTBOUND_PLUGIN = "outbound_plugin";
    /** 流水线停止丢弃。 */
    String DROP_PIPELINE_STOPPED = "pipeline_stopped";
    /** 解码失败丢弃。 */
    String DROP_DECODE = "decode";
    /** 遥测拒绝丢弃。 */
    String DROP_TELEMETRY_REJECT = "telemetry_reject";

    /** Correlate 阶段回执状态计数。 */
    default void correlateResponse(ExecutionStatus status) {
    }

    /** 出站队列深度采样。 */
    default void egressQueueDepth(MessagePriority priority, int depth) {
    }

    /** 出站发布结果计数。 */
    default void egressPublish(String channel, EnvelopeKind kind, String result) {
    }

    /** 出站丢弃计数。 */
    default void egressDrop(String reason) {
    }

    /** 出站重试计数。 */
    default void egressRetry(String channel) {
    }
}
