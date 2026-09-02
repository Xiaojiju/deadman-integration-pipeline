package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.EndpointPropertyEntity;
import com.mtfm.gateway.catalog.mapper.EndpointPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.EavPropertySupport;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CatalogEndpointPropertyRepository {

    private final EndpointPropertyMapper endpointProperties;

    public CatalogEndpointPropertyRepository(EndpointPropertyMapper endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public List<PropertyItem> list(String endpointId) {
        return endpointProperties.selectList(new QueryWrapper<EndpointPropertyEntity>()
                        .eq("endpoint_id", endpointId)
                        .orderByAsc("attribute"))
                .stream()
                .map(EavPropertySupport::toItem)
                .toList();
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
