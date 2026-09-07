package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.FunctionPropertyEntity;
import com.mtfm.gateway.catalog.mapper.FunctionPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.catalog.store.support.BatchMaps;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public class CatalogFunctionPropertyRepository {

    private final FunctionPropertyMapper functionProperties;

    public CatalogFunctionPropertyRepository(FunctionPropertyMapper functionProperties) {
        this.functionProperties = functionProperties;
    }

    public List<PropertyItem> list(String productFunctionId) {
        return listByFunctionIds(List.of(productFunctionId)).getOrDefault(productFunctionId, List.of());
    }

    public Map<String, List<PropertyItem>> listByFunctionIds(Collection<String> productFunctionIds) {
        Map<String, List<PropertyItem>> buckets = BatchMaps.buckets(productFunctionIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<FunctionPropertyEntity> rows = functionProperties.selectList(new QueryWrapper<FunctionPropertyEntity>()
                .in("product_function_id", buckets.keySet())
                .orderByAsc("attribute"));
        for (FunctionPropertyEntity row : rows) {
            buckets.computeIfAbsent(row.getProductFunctionId(), key -> new java.util.ArrayList<>())
                    .add(PropertyCodec.toItem(row));
        }
        return BatchMaps.freeze(buckets);
    }

    public void replace(String productFunctionId, List<PropertyItem> items) {
        functionProperties.delete(new QueryWrapper<FunctionPropertyEntity>()
                .eq("product_function_id", productFunctionId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            FunctionPropertyEntity row = new FunctionPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setProductFunctionId(productFunctionId);
            PropertyCodec.applyItem(row, item);
            functionProperties.insert(row);
        }
    }

    public void delete(String productFunctionId) {
        functionProperties.delete(new QueryWrapper<FunctionPropertyEntity>()
                .eq("product_function_id", productFunctionId));
    }
}
