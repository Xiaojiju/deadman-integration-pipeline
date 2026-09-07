package com.mtfm.gateway.runtime;

import com.mtfm.gateway.spi.catalog.FunctionCatalog;
import com.mtfm.gateway.spi.model.FunctionDef;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryFunctionCatalog implements FunctionCatalog {

    private final ConcurrentHashMap<String, FunctionDef> defs = new ConcurrentHashMap<>();

    InMemoryFunctionCatalog allow(String deviceId, String functionId) {
        defs.put(deviceId + "/" + functionId,
                FunctionDef.builder(functionId).accessType("WRITE").build());
        return this;
    }

    InMemoryFunctionCatalog allow(String deviceId, Set<String> functionIds) {
        functionIds.forEach(id -> allow(deviceId, id));
        return this;
    }

    @Override
    public Optional<FunctionDef> find(String deviceId, String functionId) {
        return Optional.ofNullable(defs.get(deviceId + "/" + functionId));
    }
}
