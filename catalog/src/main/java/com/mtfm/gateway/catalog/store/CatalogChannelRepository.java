package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.mapper.ChannelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 通道实体 CRUD。端点引用检查由 {@link CatalogStore} 编排。
 */
@Repository
public class CatalogChannelRepository {

    private final ChannelMapper channels;
    private final CatalogRevision revision;

    public CatalogChannelRepository(
            ChannelMapper channels, @Autowired(required = false) CatalogRevision revision) {
        this.channels = channels;
        this.revision = revision;
    }

    public ChannelEntity insert(ChannelEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, entity.getCreatedAt() == null);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIds.next());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        try {
            channels.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("通道编码已存在: " + entity.getCode(), ex);
        }
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public ChannelEntity update(ChannelEntity entity) {
        CatalogTimestamps.touch(entity::setCreatedAt, entity::setUpdatedAt, false);
        channels.updateById(entity);
        CatalogTimestamps.bump(revision);
        return entity;
    }

    public Optional<ChannelEntity> find(String channelIdOrCode) {
        ChannelEntity byId = channels.selectById(channelIdOrCode);
        if (byId != null) {
            return Optional.of(byId);
        }
        return Optional.ofNullable(channels.selectOne(new QueryWrapper<ChannelEntity>().eq("code", channelIdOrCode)));
    }

    public List<ChannelEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return channels.selectByIds(ids);
    }

    public List<ChannelEntity> list() {
        return channels.selectList(new QueryWrapper<ChannelEntity>().orderByAsc("code"));
    }

    public PageResult<ChannelEntity> page(int page, int size) {
        Page<ChannelEntity> result = channels.selectPage(
                new Page<>(CatalogPages.page(page), CatalogPages.size(size)),
                new QueryWrapper<ChannelEntity>().orderByAsc("code"));
        return PageResult.of(result);
    }

    public boolean deleteById(String channelPk) {
        boolean deleted = channels.deleteById(channelPk) > 0;
        if (deleted) {
            CatalogTimestamps.bump(revision);
        }
        return deleted;
    }
}
