package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.json.JsonMaps;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.property.PropertySchemas;

import java.util.List;

/**
 * 通道连接属性的创建与更新。
 */
final class CatalogChannelCommands {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogChannelCommands(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    ChannelEntity createChannel(ChannelWriteRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("通道 code 不能为空");
        }
        if (request.capabilityType() == null || request.capabilityType().isBlank()) {
            throw new IllegalArgumentException("capabilityType 不能为空");
        }
        if (store.findChannel(request.code()).isPresent()) {
            throw new IllegalArgumentException("通道编码已存在: " + request.code());
        }
        CapabilityDescriptor descriptor = support.requireCapability(request.capabilityType());
        List<PropertyItem> items = CatalogFormSupport.resolveProperties(request.properties(), request.connection());
        CatalogConnectionSupport.validateConnection(descriptor, items);
        items = CatalogConnectionSupport.sealSecrets(items, descriptor.connectionSchema(), store.secretCodec());
        ChannelEntity entity = new ChannelEntity();
        entity.setCode(request.code());
        entity.setCapabilityType(request.capabilityType());
        entity.setConnection(JsonMaps.write(PropertySchemas.toValueMap(items)));
        entity.setEnabled(request.enabled());
        ChannelEntity saved = store.saveChannel(entity);
        store.properties().replaceChannelProperties(saved.getId(), items);
        return saved;
    }

    ChannelEntity updateChannel(String channelIdOrCode, ChannelWriteRequest request) {
        ChannelEntity entity = store.findChannel(channelIdOrCode)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelIdOrCode));
        CapabilityDescriptor descriptor = support.requireCapability(entity.getCapabilityType());
        if (request != null) {
            if (request.properties() != null || request.connection() != null) {
                List<PropertyItem> items = CatalogConnectionSupport.restoreSecrets(
                        CatalogFormSupport.resolveProperties(request.properties(), request.connection()),
                        store.loadChannelProperties(entity),
                        descriptor.connectionSchema());
                CatalogConnectionSupport.validateConnection(descriptor, items);
                items = CatalogConnectionSupport.sealSecrets(
                        items, descriptor.connectionSchema(), store.secretCodec());
                entity.setConnection(JsonMaps.write(PropertySchemas.toValueMap(items)));
                store.properties().replaceChannelProperties(entity.getId(), items);
            }
            if (request.enabled() != null) {
                entity.setEnabled(request.enabled());
            }
        }
        return store.updateChannel(entity);
    }
}
