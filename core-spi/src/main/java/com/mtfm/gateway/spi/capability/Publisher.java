package com.mtfm.gateway.spi.capability;

import com.mtfm.gateway.spi.model.OutboundMessage;
import com.mtfm.gateway.spi.model.PublishResult;

/**
 * 北向发布器。同一 {@link #channel()} 运行时只允许注册一个实现。
 *
 * <p>禁止自建出站业务队列；重试由核心 Egress 调度。
 *
 * <p>使用示例：
 * <pre>{@code
 * public final class CloudPublisher implements Publisher {
 *     public String channel() { return Channels.CLOUD; }
 *     public PublishResult publish(OutboundMessage message) {
 *         try {
 *             mqttClient.publish(message.deviceId(), message.body());
 *             return PublishResult.success();
 *         } catch (Exception ex) {
 *             return PublishResult.failed("cloud", ex.getMessage(), true);
 *         }
 *     }
 * }
 * }</pre>
 */
public interface Publisher {

    /** 本发布器对应的逻辑通道名。 */
    String channel();

    /** 是否支持该消息；默认匹配 {@code channelHint}。 */
    default boolean support(OutboundMessage message) {
        return channel().equals(message.channelHint());
    }

    /**
     * 同步发布已贴胶出站消息。
     *
     * @param message 不可变出站消息
     * @return 发布结果；失败时核心按 {@link com.mtfm.gateway.spi.model.Failure#retryable()} 决定是否重试
     */
    PublishResult publish(OutboundMessage message);
}
