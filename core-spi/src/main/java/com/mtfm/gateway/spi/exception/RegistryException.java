package com.mtfm.gateway.spi.exception;

/**
 * 装配失败异常（重复绑定、重复通道等）。
 */
public class RegistryException extends RuntimeException {

    /** 以消息创建异常。 */
    public RegistryException(String message) {
        super(message);
    }
}
