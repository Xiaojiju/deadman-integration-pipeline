package com.mtfm.gateway.catalog.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mtfm.gateway.catalog.entity.ChannelPropertyEntity;
import com.mtfm.gateway.catalog.mapper.ChannelPropertyMapper;
import com.mtfm.gateway.catalog.id.SnowflakeIds;
import com.mtfm.gateway.catalog.store.support.EavPropertySupport;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CatalogChannelPropertyRepository {

    private final ChannelPropertyMapper channelProperties;

    public CatalogChannelPropertyRepository(ChannelPropertyMapper channelProperties) {
        this.channelProperties = channelProperties;
    }

    public List<PropertyItem> list(String channelId) {
        return channelProperties.selectList(new QueryWrapper<ChannelPropertyEntity>()
                        .eq("channel_id", channelId)
                        .orderByAsc("attribute"))
                .stream()
                .map(EavPropertySupport::toItem)
                .toList();
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
            EavPropertySupport.applyItem(row, item);
            channelProperties.insert(row);
        }
    }

    public void delete(String channelId) {
        channelProperties.delete(new QueryWrapper<ChannelPropertyEntity>().eq("channel_id", channelId));
    }
}
