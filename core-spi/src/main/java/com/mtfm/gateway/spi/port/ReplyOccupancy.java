package com.mtfm.gateway.spi.port;

/**
 * 应答等待占用查询。定时下发在同功能仍在 waiter 中时跳过本拍。
 */
public interface ReplyOccupancy {

    boolean awaiting(String deviceId, String functionId);
}
