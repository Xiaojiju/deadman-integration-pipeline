package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceBinding;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.property.PropertyItem;
import com.mtfm.gateway.spi.secret.SecretCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设备绑定 SPI 投影。只读 EAV 通道/端点属性，不负责 CRUD。
 *
 * <p>
 * 使用示例：{@code catalog.findEndpoints("door-1")}
 */
@Service
public class CatalogBindingCatalog implements DeviceBindingCatalog {

    private final CatalogDeviceRepository devices;
    private final CatalogChannelRepository channels;
    private final CatalogEavLoader eav;
    private final SecretCodec secretCodec;

    public CatalogBindingCatalog(
            CatalogDeviceRepository devices,
            CatalogChannelRepository channels,
            CatalogEavLoader eav,
            ObjectProvider<SecretCodec> secretCodec) {
        this.devices = devices;
        this.channels = channels;
        this.eav = eav;
        this.secretCodec = secretCodec == null
                ? SecretCodec.identity()
                : secretCodec.getIfAvailable(SecretCodec::identity);
    }

    @Override
    public Optional<DeviceBinding> findDevice(String deviceId) {
        List<DeviceEndpointBinding> found = findEndpoints(deviceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DeviceBinding(deviceId, uniqueCapabilityType(found)));
    }

    @Override
    public List<DeviceEndpointBinding> findEndpoints(String deviceId) {
        Optional<DeviceEntity> device = devices.findByCode(deviceId);
        if (device.isEmpty()) {
            return List.of();
        }
        List<DeviceEndpointEntity> rows = devices.listEndpoints(device.get().getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> channelIds = rows.stream()
                .map(row -> row.getChannelId())
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        List<ChannelEntity> channelRows = channels.findByIds(channelIds);
        Map<String, ChannelEntity> channelById = new LinkedHashMap<>();
        for (ChannelEntity channel : channelRows) {
            channelById.put(channel.getId(), channel);
        }
        Map<String, List<PropertyItem>> channelProps = eav.loadChannelProperties(channelRows);
        Map<String, List<PropertyItem>> endpointProps = eav.loadEndpointProperties(rows);
        List<DeviceEndpointBinding> result = new ArrayList<>();
        for (DeviceEndpointEntity row : rows) {
            ChannelEntity channel = channelById.get(row.getChannelId());
            if (channel == null) {
                throw new IllegalStateException("端点引用了不存在的通道: device="
                        + device.get().getDeviceCode() + " channelId=" + row.getChannelId());
            }
            Attributes connection = Attributes.from(openConnectionSecrets(
                    PropertyCodec.toMap(channelProps.getOrDefault(channel.getId(), List.of()))));
            Attributes address = Attributes.from(PropertyCodec.toMap(
                    endpointProps.getOrDefault(row.getId(), List.of())));
            result.add(new DeviceEndpointBinding(
                    device.get().getDeviceCode(),
                    channel.getCode(),
                    channel.getCapabilityType(),
                    connection,
                    address,
                    !Boolean.FALSE.equals(channel.getEnabled())));
        }
        return List.copyOf(result);
    }

    static String uniqueCapabilityType(List<DeviceEndpointBinding> endpoints) {
        Set<String> types = new LinkedHashSet<>();
        for (DeviceEndpointBinding endpoint : endpoints) {
            if (endpoint.capabilityType() != null && !endpoint.capabilityType().isBlank()) {
                types.add(endpoint.capabilityType().toUpperCase());
            }
        }
        if (types.size() > 1) {
            throw new IllegalStateException("设备绑定了多种南向能力，当前运行时一设备一协议: " + types);
        }
        return endpoints.get(0).capabilityType();
    }

    private Map<String, Object> openConnectionSecrets(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return values == null ? Map.of() : values;
        }
        Map<String, Object> opened = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                opened.put(entry.getKey(), secretCodec.open(text));
            } else {
                opened.put(entry.getKey(), value);
            }
        }
        return opened;
    }
}
