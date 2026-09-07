package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.ChannelPropertyEntity;
import com.mtfm.gateway.catalog.mapper.ChannelPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.catalog.store.support.BatchMaps;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public class CatalogChannelPropertyRepository {

    private final ChannelPropertyMapper channelProperties;

    public CatalogChannelPropertyRepository(ChannelPropertyMapper channelProperties) {
        this.channelProperties = channelProperties;
    }

    public List<PropertyItem> list(String channelId) {
        return listByChannelIds(List.of(channelId)).getOrDefault(channelId, List.of());
    }

    public Map<String, List<PropertyItem>> listByChannelIds(Collection<String> channelIds) {
        Map<String, List<PropertyItem>> buckets = BatchMaps.buckets(channelIds);
        if (buckets.isEmpty()) {
            return Map.of();
        }
        List<ChannelPropertyEntity> rows = channelProperties.selectList(new QueryWrapper<ChannelPropertyEntity>()
                .in("channel_id", buckets.keySet())
                .orderByAsc("attribute"));
        for (ChannelPropertyEntity row : rows) {
            buckets.computeIfAbsent(row.getChannelId(), key -> new java.util.ArrayList<>())
                    .add(PropertyCodec.toItem(row));
        }
        return BatchMaps.freeze(buckets);
    }

    public void replace(String channelId, List<PropertyItem> items) {
        channelProperties.delete(new QueryWrapper<ChannelPropertyEntity>().eq("channel_id", channelId));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PropertyItem item : items) {
            ChannelPropertyEntity row = new ChannelPropertyEntity();
            row.setId(SnowflakeIds.next());
            row.setChannelId(channelId);
            PropertyCodec.applyItem(row, item);
            channelProperties.insert(row);
        }
    }

    public void delete(String channelId) {
        channelProperties.delete(new QueryWrapper<ChannelPropertyEntity>().eq("channel_id", channelId));
    }
}
