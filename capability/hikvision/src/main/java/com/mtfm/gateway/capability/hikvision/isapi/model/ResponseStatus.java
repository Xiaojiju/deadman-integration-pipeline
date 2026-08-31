package com.mtfm.gateway.capability.hikvision.isapi.model;

/**
 * 海康 ISAPI 通用响应，对齐旧网关 {@code ResponseStatus}。
 */
public final class ResponseStatus {
    /**
     * 状态码
     */
    private int statusCode;
    /**
     * 状态字符串
     */
    private String statusString;

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return statusCode == 1 || statusCode == 0;
    }

    /**
     * 获取状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取状态字符串
     */
    public String getStatusString() {
        return statusString;
    }
}
