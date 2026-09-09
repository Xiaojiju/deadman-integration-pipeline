package com.mtfm.gateway.spi.presence;

import com.mtfm.gateway.spi.model.Attributes;

/**
 * 已归因到通道的一帧在线信号，供 {@link PresencePlugin} 翻译。
 *
 * @param channelId 通道业务编码
 * @param topic     可选来源 topic
 * @param payload   原始属性
 */
public record PresenceSignal(String channelId, String topic, Attributes payload) {
}
