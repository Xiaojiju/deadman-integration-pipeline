package com.mtfm.gateway.spi.model;

import java.util.Objects;

/**
 * {@link com.mtfm.gateway.spi.capability.Publisher#publish} 的同步返回。重试由核心 Egress 调度。
 */
public sealed interface PublishResult {

    /** 发布成功。 */
    record Success() implements PublishResult {
    }

    /** 发布失败，携带 {@link Failure}。 */
    record Failed(Failure failure) implements PublishResult {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /** 创建成功结果。 */
    static Success success() {
        return new Success();
    }

    static Failed failed(Failure failure) {
        return new Failed(failure);
    }

    static Failed failed(String source, String message, boolean retryable) {
        return new Failed(Failure.publisherError(source, message, retryable));
    }
}
