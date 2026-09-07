package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.mapper.ProductFunctionMapper;
import com.mtfm.gateway.catalog.mapper.ProductMapper;
import com.mtfm.gateway.spi.property.ValueAccessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 产品与产品功能实体 CRUD。跨表删除（属性、设备引用）由 {@link CatalogStore} 编排。
 */
@Repository
public class CatalogProductRepository {

    private final ProductMapper products;
    private final ProductFunctionMapper functions;
    private final CatalogRevision revision;

    public CatalogProductRepository(
            ProductMapper products,
            ProductFunctionMapper functions,
            @Autowired(required = false) CatalogRevision revision) {
        this.products = products;
        this.functions = functions;
        this.revision = revision;
    }

    public ProductEntity insert(ProductEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        try {
            products.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("产品编码已存在: " + entity.getCode(), ex);
        }
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public ProductEntity update(ProductEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        products.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public Optional<ProductEntity> findById(String productId) {
        return Optional.ofNullable(products.selectById(productId));
    }

    public Optional<ProductEntity> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(products.selectOne(new QueryWrapper<ProductEntity>().eq("code", code)));
    }

    public List<ProductEntity> list() {
        return products.selectList(new QueryWrapper<ProductEntity>().orderByAsc("code"));
    }

    public PageResult<ProductEntity> page(int page, int size) {
        Page<ProductEntity> result = products.selectPage(
                new Page<>(CatalogPages.page(page), CatalogPages.size(size)),
                new QueryWrapper<ProductEntity>().orderByAsc("code"));
        return PageResult.of(result);
    }

    public boolean deleteById(String productId) {
        boolean deleted = products.deleteById(productId) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public ProductFunctionEntity insertFunction(ProductFunctionEntity entity) {
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getSortIndex() == null) {
            entity.setSortIndex(0);
        }
        if (entity.getWriteAccessType() == null || entity.getWriteAccessType().isBlank()) {
            entity.setWriteAccessType(ValueAccessType.VALUE.name());
        }
        functions.insert(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public ProductFunctionEntity updateFunction(ProductFunctionEntity entity) {
        functions.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public List<ProductFunctionEntity> listFunctions(String productId) {
        return functions.selectList(new QueryWrapper<ProductFunctionEntity>()
                .eq("product_id", productId)
                .orderByAsc("sort_index"));
    }

    public Optional<ProductFunctionEntity> findFunction(String productId, String functionId) {
        return Optional.ofNullable(functions.selectOne(new QueryWrapper<ProductFunctionEntity>()
                .eq("product_id", productId)
                .eq("function_id", functionId)));
    }

    public boolean deleteFunctionById(String functionPk) {
        boolean deleted = functions.deleteById(functionPk) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public void deleteFunctionsByProduct(String productId) {
        functions.delete(new QueryWrapper<ProductFunctionEntity>().eq("product_id", productId));
        CatalogTimestamps.bump(revision);
    }
}
