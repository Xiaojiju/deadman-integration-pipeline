package com.mtfm.gateway.capability.cloud;

import com.mtfm.gateway.spi.model.OutboundMessage;

/**
 * 北向扇出腿。由 {@link CloudPublisher} Hub 调用；失败不得抛回流水线。
 */
public interface NorthboundSink {

    /**
     * 发布出站消息。实现须自行吞掉失败（日志 / 指标），禁止抛出让 Hub 回压南向。
     */
    void publish(OutboundMessage message);
}
