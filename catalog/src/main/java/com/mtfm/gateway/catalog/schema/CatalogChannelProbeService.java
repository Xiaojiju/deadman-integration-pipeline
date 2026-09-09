package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.ChannelProbeItemView;
import com.mtfm.gateway.catalog.dto.ChannelProbeRequestBody;
import com.mtfm.gateway.catalog.dto.ChannelProbeView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.catalog.store.support.PropertyCodec;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.presence.DeviceOnlineHint;
import com.mtfm.gateway.spi.presence.DevicePresencePort;
import com.mtfm.gateway.spi.probe.ChannelProbe;
import com.mtfm.gateway.spi.probe.ChannelProbeRegistry;
import com.mtfm.gateway.spi.probe.ChannelProbeRequest;
import com.mtfm.gateway.spi.probe.ChannelProbeResult;
import com.mtfm.gateway.spi.probe.DiscoveredDevice;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通道探针：扫描子设备并按 EhomeID/deviceCode upsert，写在线状态。新设备不自动 load。
 */
@Service
public class CatalogChannelProbeService {

    static final String ACTION_CREATED = "created";
    static final String ACTION_SERIAL_UPDATED = "serialUpdated";
    static final String ACTION_UNCHANGED = "unchanged";

    private final CatalogStore store;
    private final CatalogFormSupport support;
    private final CatalogDeviceCommands devices;
    private final CatalogApplyService applyService;
    private final ChannelProbeRegistry probes;
    private final DevicePresencePort presence;

    public CatalogChannelProbeService(
            CatalogStore store,
            CatalogFormSupport support,
            CatalogDeviceCommands devices,
            CatalogApplyService applyService,
            ChannelProbeRegistry probes,
            DevicePresencePort presence) {
        this.store = store;
        this.support = support;
        this.devices = devices;
        this.applyService = applyService;
        this.probes = probes;
        this.presence = presence;
    }

    @Transactional
    public ChannelProbeView probe(String channelId, ChannelProbeRequestBody body) {
        ChannelEntity channel = store.findChannel(channelId)
                .orElseThrow(() -> new IllegalArgumentException("通道不存在: " + channelId));
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            throw new IllegalArgumentException("通道已停用: " + channel.getCode());
        }
        CapabilityDescriptor descriptor = support.requireCapability(channel.getCapabilityType());
        if (!descriptor.probeSupported()) {
            throw new IllegalArgumentException("能力不支持通道扫描: " + channel.getCapabilityType());
        }
        ChannelProbe probe = probes.find(channel.getCapabilityType())
                .orElseThrow(() -> new IllegalArgumentException("未装配通道探针: " + channel.getCapabilityType()));
        ProductEntity product = requireProbeProduct(body.productId(), channel.getCapabilityType());
        ChannelProbeResult result;
        try {
            result = probe.scan(new ChannelProbeRequest(
                    channel.getCode(), store.openedConnection(channel)));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("扫描通道设备失败: " + ex.getMessage(), ex);
        }
        List<DiscoveredDevice> discovered = result.devices();
        List<ChannelProbeItemView> items = new ArrayList<>();
        int created = 0;
        int serialUpdated = 0;
        int unchanged = 0;
        List<DeviceOnlineHint> hints = new ArrayList<>();
        for (DiscoveredDevice item : discovered) {
            ChannelProbeItemView view = upsert(channel, product, item);
            items.add(view);
            hints.add(new DeviceOnlineHint(item.deviceCode(), item.online()));
            switch (view.action()) {
                case ACTION_CREATED -> created++;
                case ACTION_SERIAL_UPDATED -> serialUpdated++;
                default -> unchanged++;
            }
        }
        presence.apply(hints);
        return new ChannelProbeView(
                channel.getId(),
                channel.getCode(),
                product.getId(),
                discovered.size(),
                created,
                serialUpdated,
                unchanged,
                List.copyOf(items));
    }

    private ProductEntity requireProbeProduct(String productId, String capabilityType) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        ProductEntity product = store.findProduct(productId)
                .orElseGet(() -> store.findProductByCode(productId)
                        .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId)));
        ProductTypeEntity type = store.findProductType(product.getProductTypeId())
                .orElseThrow(() -> new IllegalArgumentException("产品类型不存在: " + product.getProductTypeId()));
        if (!ProductTypeEntity.ACCESS_CONTROL.equals(type.getCode())) {
            throw new IllegalArgumentException("扫描只接受门禁产品，当前类型: " + type.getCode());
        }
        List<ProductFunctionEntity> functions = store.listFunctions(product.getId());
        if (!functions.isEmpty()
                && functions.stream().noneMatch(fn -> capabilityType.equalsIgnoreCase(fn.getCapabilityType()))) {
            throw new IllegalArgumentException("产品功能与通道能力不匹配: " + capabilityType);
        }
        return product;
    }

    private ChannelProbeItemView upsert(ChannelEntity channel, ProductEntity product, DiscoveredDevice item) {
        String serial = addressSerial(item);
        DeviceEntity existing = store.findDeviceByCode(item.deviceCode()).orElse(null);
        if (existing == null) {
            DeviceEntity created = devices.createDevice(new DeviceWriteRequest(
                    item.deviceCode(),
                    product.getId(),
                    item.name(),
                    null,
                    true));
            devices.createEndpoint(created.getDeviceCode(), new DeviceEndpointWriteRequest(
                    channel.getId(), addressProperties(item)));
            return new ChannelProbeItemView(item.deviceCode(), item.name(), serial, item.online(), ACTION_CREATED);
        }
        DeviceEndpointEntity endpoint = store.findEndpoint(existing.getId(), channel.getId()).orElse(null);
        if (endpoint == null) {
            devices.createEndpoint(existing.getDeviceCode(), new DeviceEndpointWriteRequest(
                    channel.getId(), addressProperties(item)));
            return new ChannelProbeItemView(
                    existing.getDeviceCode(), existing.getName(), serial, item.online(), ACTION_CREATED);
        }
        String previous = currentSerial(endpoint);
        if (serial.equals(previous)) {
            return new ChannelProbeItemView(
                    existing.getDeviceCode(), existing.getName(), serial, item.online(), ACTION_UNCHANGED);
        }
        boolean loaded = applyService.isLoaded(existing.getDeviceCode());
        devices.updateEndpoint(endpoint.getId(), new DeviceEndpointWriteRequest(
                channel.getId(), addressProperties(item)));
        if (loaded) {
            applyService.unload(existing.getDeviceCode());
            applyService.load(existing.getDeviceCode());
        }
        return new ChannelProbeItemView(
                existing.getDeviceCode(), existing.getName(), serial, item.online(), ACTION_SERIAL_UPDATED);
    }

    private String currentSerial(DeviceEndpointEntity endpoint) {
        Map<String, Object> values = PropertyCodec.toMap(store.loadEndpointProperties(endpoint));
        Object serial = values.get("deviceSerialNo");
        return serial == null ? "" : String.valueOf(serial);
    }

    private static String addressSerial(DiscoveredDevice item) {
        if (item.address() == null) {
            return "";
        }
        return item.address().get("deviceSerialNo")
                .map(String::valueOf)
                .orElse("");
    }

    private static List<PropertyItem> addressProperties(DiscoveredDevice item) {
        if (item.address() == null || item.address().isEmpty()) {
            return List.of();
        }
        return PropertyCodec.fromMap(item.address().values());
    }
}
