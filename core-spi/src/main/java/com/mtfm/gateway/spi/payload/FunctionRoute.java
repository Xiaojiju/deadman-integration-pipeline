package com.mtfm.gateway.spi.payload;

/**
 * 产品功能 Topic 路由（slot 名，非完整 topic）。
 *
 * @param publishTopicSlot  WRITE 发布 slot，空则用 default_pub
 * @param subscribeTopicSlot READ 订阅 slot，空则用 default_sub
 */
public record FunctionRoute(String publishTopicSlot, String subscribeTopicSlot) {

    public static FunctionRoute empty() {
        return new FunctionRoute(null, null);
    }
}
