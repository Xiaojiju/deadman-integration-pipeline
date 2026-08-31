package com.mtfm.gateway.spi.exception;

import com.mtfm.gateway.spi.model.Failure;

/**
 * {@link com.mtfm.gateway.spi.capability.Driver#decode} 失败时抛出，携带结构化 {@link Failure}。
 */
public class DecodeException extends RuntimeException {

    private final Failure failure;

    /** 以失败描述创建异常。 */
    public DecodeException(Failure failure) {
        super(failure.message());
        this.failure = failure;
    }

    /** 以失败描述与根因创建异常。 */
    public DecodeException(Failure failure, Throwable cause) {
        super(failure.message(), cause);
        this.failure = failure;
    }

    /** 返回结构化失败描述。 */
    public Failure failure() {
        return failure;
    }
}
