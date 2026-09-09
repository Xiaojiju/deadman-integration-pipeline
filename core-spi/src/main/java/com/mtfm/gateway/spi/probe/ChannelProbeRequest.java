package com.mtfm.gateway.spi.probe;

import com.mtfm.gateway.spi.model.Attributes;

/**
 * 通道探针请求。connection 已解密。
 *
 * @param channelId  通道业务编码
 * @param connection 通道连接参数
 */
public record ChannelProbeRequest(String channelId, Attributes connection) {
}
