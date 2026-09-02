package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.FunctionPropertyEntity;
import com.mtfm.gateway.catalog.mapper.FunctionPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.EavPropertySupport;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CatalogFunctionPropertyRepository {

    private final FunctionPropertyMapper functionProperties;

    public CatalogFunctionPropertyRepository(FunctionPropertyMapper functionProperties) {
        this.functionProperties = functionProperties;
    }

    public List<PropertyItem> list(String productFunctionId) {
        return functionProperties.selectList(new QueryWrapper<FunctionPropertyEntity>()
                        .eq("product_function_id", productFunctionId)
                        .orderByAsc("attribute"))
                .stream()
                .map(EavPropertySupport::toItem)
                .toList();
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
            EavPropertySupport.applyItem(row, item);
            functionProperties.insert(row);
        }
    }

    public void delete(String productFunctionId) {
        functionProperties.delete(new QueryWrapper<FunctionPropertyEntity>()
                .eq("product_function_id", productFunctionId));
    }
}
