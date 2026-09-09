package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.mapper.ProductTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 产品类型 CRUD。
 */
@Repository
public class CatalogProductTypeRepository {

    private final ProductTypeMapper types;
    private final CatalogRevision revision;

    public CatalogProductTypeRepository(
            ProductTypeMapper types,
            @Autowired(required = false) CatalogRevision revision) {
        this.types = types;
        this.revision = revision;
    }

    public ProductTypeEntity insert(ProductTypeEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        try {
            types.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("产品类型编码已存在: " + entity.getCode(), ex);
        }
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public ProductTypeEntity update(ProductTypeEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        types.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public Optional<ProductTypeEntity> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.selectById(id));
    }

    public Optional<ProductTypeEntity> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.selectOne(new QueryWrapper<ProductTypeEntity>().eq("code", code)));
    }

    public Optional<ProductTypeEntity> find(String idOrCode) {
        return findById(idOrCode).or(() -> findByCode(idOrCode));
    }

    public List<ProductTypeEntity> list() {
        return types.selectList(new QueryWrapper<ProductTypeEntity>().orderByAsc("code"));
    }

    public boolean deleteById(String id) {
        boolean deleted = types.deleteById(id) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }

    public long countById(String id) {
        return types.selectCount(new QueryWrapper<ProductTypeEntity>().eq("id", id));
    }
}
