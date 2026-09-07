package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.property.PropertyItem;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通道连接属性的创建与更新。
 */
@Component
final class CatalogChannelCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogChannelCommands(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    ChannelEntity createChannel(ChannelWriteRequest request) {
        if (store.findChannel(request.code()).isPresent()) {
            throw new IllegalArgumentException("通道编码已存在: " + request.code());
        }
        CapabilityDescriptor descriptor = support.requireCapability(request.capabilityType());
        List<PropertyItem> items = CatalogFormSupport.resolveProperties(request.properties());
        CatalogConnectionSupport.validateConnection(descriptor, items);
        items = CatalogConnectionSupport.sealSecrets(items, descriptor.connectionSchema(), store.secretCodec());
        ChannelEntity entity = new ChannelEntity();
        entity.setCode(request.code());
        entity.setCapabilityType(request.capabilityType());
        entity.setConnection(JsonMaps.EMPTY_OBJECT);
        entity.setEnabled(request.enabled());
        ChannelEntity saved = store.saveChannel(entity);
        store.properties().replaceChannelProperties(saved.getId(), items);
        return saved;
    }

    ChannelEntity updateChannel(String channelIdOrCode, ChannelWriteRequest request) {
        ChannelEntity entity = store.findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        CapabilityDescriptor descriptor = support.requireCapability(entity.getCapabilityType());
        if (request.properties() != null) {
            List<PropertyItem> items = CatalogConnectionSupport.restoreSecrets(
                    CatalogFormSupport.resolveProperties(request.properties()),
                    store.loadChannelProperties(entity),
                    descriptor.connectionSchema());
            CatalogConnectionSupport.validateConnection(descriptor, items);
            items = CatalogConnectionSupport.sealSecrets(
                    items, descriptor.connectionSchema(), store.secretCodec());
            entity.setConnection(JsonMaps.EMPTY_OBJECT);
            store.properties().replaceChannelProperties(entity.getId(), items);
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        return store.updateChannel(entity);
    }
}
