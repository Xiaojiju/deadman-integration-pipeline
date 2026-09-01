package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.payload.FunctionRoute;
import com.mtfm.gateway.spi.payload.TopicCatalog;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.port.MqttSubscribeRoute;
import com.mtfm.gateway.spi.port.MqttSubscribeRouteCatalog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 从产品功能 + 设备 address 解析 MQTT READ 订阅路由。 */
@Service
public class CatalogMqttSubscribeRoutes implements MqttSubscribeRouteCatalog {

    private static final String CAPABILITY_MQTT = "MQTT";

    private final CatalogStore store;

    public CatalogMqttSubscribeRoutes(CatalogStore store) {
        this.store = store;
    }

    @Override
    public List<MqttSubscribeRoute> routesForDevice(String deviceCode, Map<String, Object> addressValues) {
        Optional<DeviceEntity> device = store.findDeviceByCode(deviceCode);
        if (device.isEmpty() || addressValues == null || addressValues.isEmpty()) {
            return List.of();
        }
        TopicCatalog catalog = TopicCatalog.fromAddressMap(addressValues);
        return store.listFunctions(device.get().getProductId()).stream()
                .filter(function -> CAPABILITY_MQTT.equalsIgnoreCase(function.getCapabilityType()))
                .filter(function -> "READ".equalsIgnoreCase(function.getAccessType()))
                .map(function -> toRoute(device.get(), function, catalog))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MqttSubscribeRoute> toRoute(
            DeviceEntity device, ProductFunctionEntity function, TopicCatalog catalog) {
        Map<String, String> topicOverrides = store.properties().listDeviceTopicOverrides(
                device.getId(), function.getFunctionId());
        TopicCatalog effective = catalog.withOverrides(topicOverrides);
        FunctionRoute route = new FunctionRoute(function.getPublishTopicSlot(), function.getSubscribeTopicSlot());
        try {
            TopicRouteResolver.ResolvedRoute resolved = TopicRouteResolver.resolve(
                    effective, route, Map.of(), false);
            if (resolved.subscribeTopic() == null || resolved.subscribeTopic().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new MqttSubscribeRoute(resolved.subscribeTopic(), function.getFunctionId()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
