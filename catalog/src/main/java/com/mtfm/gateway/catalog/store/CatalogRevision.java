package com.mtfm.gateway.catalog.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 配置域版本戳。目录写成功后 bump，{@link CachingFunctionCatalog} 丢弃过期缓存。
 */
@Component
public class CatalogRevision {

    private final AtomicLong epoch = new AtomicLong();

    public long epoch() {
        return epoch.get();
    }

    public void bump() {
        epoch.incrementAndGet();
    }
}
