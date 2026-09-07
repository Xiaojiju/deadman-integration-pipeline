package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.FunctionDef;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link FunctionCatalog} 内存投影。按 {@link CatalogRevision} 失效，避免每次 find 打多表。
 */
@Service
@Primary
public class CachingFunctionCatalog implements FunctionCatalog {

    private final CatalogFunctionProjection projection;
    private final CatalogRevision revision;
    private final ConcurrentHashMap<String, Optional<FunctionDef>> cache = new ConcurrentHashMap<>();
    private volatile long cachedEpoch = Long.MIN_VALUE;

    public CachingFunctionCatalog(CatalogFunctionProjection projection, CatalogRevision revision) {
        this.projection = projection;
        this.revision = revision;
    }

    @Override
    public Optional<FunctionDef> find(String deviceId, String functionId) {
        long epoch = revision.epoch();
        if (epoch != cachedEpoch) {
            cache.clear();
            cachedEpoch = epoch;
        }
        if (deviceId == null || functionId == null || deviceId.isBlank() || functionId.isBlank()) {
            return Optional.empty();
        }
        String key = deviceId + "\0" + functionId;
        return cache.computeIfAbsent(key, ignored -> projection.find(deviceId, functionId));
    }
}
