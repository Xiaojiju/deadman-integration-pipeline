package com.mtfm.gateway.catalog.store.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IN 查询结果按外键分桶。 */
public final class BatchMaps {

    private BatchMaps() {
    }

    public static boolean isEmpty(Collection<?> ids) {
        return ids == null || ids.isEmpty();
    }

    public static <T> Map<String, List<T>> buckets(Collection<String> ids) {
        Map<String, List<T>> map = new LinkedHashMap<>();
        if (ids == null) {
            return map;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                map.putIfAbsent(id, new ArrayList<>());
            }
        }
        return map;
    }

    public static <T> Map<String, List<T>> freeze(Map<String, List<T>> buckets) {
        Map<String, List<T>> frozen = new LinkedHashMap<>();
        buckets.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Map.copyOf(frozen);
    }
}
