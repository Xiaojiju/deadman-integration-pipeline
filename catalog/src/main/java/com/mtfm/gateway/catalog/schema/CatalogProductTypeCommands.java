package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ProductTypeWriteRequest;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import org.springframework.stereotype.Component;

/**
 * 产品类型 CRUD。
 */
@Component
final class CatalogProductTypeCommands {

    private final CatalogStore store;

    CatalogProductTypeCommands(CatalogStore store) {
        this.store = store;
    }

    ProductTypeEntity create(ProductTypeWriteRequest request) {
        if (store.findProductType(request.code()).isPresent()) {
            throw new IllegalArgumentException("产品类型编码已存在: " + request.code());
        }
        ProductTypeEntity entity = new ProductTypeEntity();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setDescription(blankToNull(request.description()));
        return store.saveProductType(entity);
    }

    ProductTypeEntity update(String idOrCode, ProductTypeWriteRequest request) {
        ProductTypeEntity entity = store.findProductType(idOrCode)
                .orElseThrow(() -> new IllegalArgumentException("产品类型不存在: " + idOrCode));
        if (request != null) {
            if (request.name() != null && !request.name().isBlank()) {
                entity.setName(request.name().trim());
            }
            if (request.description() != null) {
                entity.setDescription(blankToNull(request.description()));
            }
        }
        return store.updateProductType(entity);
    }

    boolean delete(String idOrCode) {
        return store.deleteProductType(idOrCode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
