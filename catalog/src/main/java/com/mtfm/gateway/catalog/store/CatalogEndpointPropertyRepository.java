package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.EndpointPropertyEntity;
import com.mtfm.gateway.catalog.mapper.EndpointPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.EavPropertySupport;
import com.mtfm.gateway.catalog.store.support.BatchMaps;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public class CatalogEndpointPropertyRepository {

    private final EndpointPropertyMapper endpointProperties;

    public CatalogEndpointPropertyRepository(EndpointPropertyMapper endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public List<PropertyItem> list(String endpointId) {
        return listByEndpointIds(List.of(endpointId)).getOrDefault(endpointId, List.of());
    }

    public Map<String, List<PropertyItem>> listByEndpointIds(Collection<String> endpointIds) {
        Map<String, List<PropertyItem>> buckets = BatchMaps.buckets(endpointIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<EndpointPropertyEntity> rows = endpointProperties.selectList(new QueryWrapper<EndpointPropertyEntity>()
                .in("endpoint_id", buckets.keySet())
                .orderByAsc("attribute"));
        for (EndpointPropertyEntity row : rows) {
            buckets.computeIfAbsent(row.getEndpointId(), key -> new java.util.ArrayList<>())
                    .add(EavPropertySupport.toItem(row));
        }
        return BatchMaps.freeze(buckets);
    }

    public void replace(String endpointId, List<PropertyItem> items) {
        endpointProperties.delete(new QueryWrapper<EndpointPropertyEntity>().eq("endpoint_id", endpointId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            EndpointPropertyEntity row = new EndpointPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setEndpointId(endpointId);
            EavPropertySupport.applyItem(row, item);
            endpointProperties.insert(row);
        }
    }

    public void delete(String endpointId) {
        endpointProperties.delete(new QueryWrapper<EndpointPropertyEntity>().eq("endpoint_id", endpointId));
    }
}
