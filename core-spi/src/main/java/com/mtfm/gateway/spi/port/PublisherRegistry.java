package com.mtfm.gateway.spi.port;

import com.mtfm.gateway.spi.capability.Publisher;

/**
 * 北向发布器登记端口。同一 {@code channelHint} 只绑一个 {@link com.mtfm.gateway.spi.capability.Publisher}。
 *
 * <p>使用示例：
 * <pre>{@code
 * publisherRegistry.register(new CloudPublisher());
 * }</pre>
 */
public interface PublisherRegistry {

    /** 注册北向发布器。 */
    boolean register(Publisher publisher);
}
