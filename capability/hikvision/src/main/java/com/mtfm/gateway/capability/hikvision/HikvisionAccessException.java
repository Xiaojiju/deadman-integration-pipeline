package com.mtfm.gateway.capability.hikvision;

/**
 * 海康 ISAPI 调用失败时抛出，由 {@link HikvisionExecutor} 转为 {@link com.mtfm.gateway.spi.model.ExecutionResult}。
 */
public final class HikvisionAccessException extends RuntimeException {

    private final boolean retryable;

    public HikvisionAccessException(String message) {
        this(message, false);
    }

    public HikvisionAccessException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public HikvisionAccessException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public HikvisionAccessException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
