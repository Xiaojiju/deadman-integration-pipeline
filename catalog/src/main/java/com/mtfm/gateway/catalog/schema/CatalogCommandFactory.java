package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.payload.PayloadDefinitionResolver;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.payload.TopicCatalog;
import com.mtfm.gateway.spi.payload.TopicRouteResolver;
import com.mtfm.gateway.spi.property.PropertySchemas;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将目录配置装配为运行时 {@link FunctionCommand}。
 */
final class CatalogCommandFactory {

    private final CatalogStore store;
    private final CatalogFormSupport support;

    CatalogCommandFactory(CatalogStore store, CatalogFormSupport support) {
        this.store = store;
        this.support = support;
    }

    FunctionCommand buildCommand(String deviceCode, DeviceCommandRequest request) {
        if (request == null || request.functionId() == null || request.functionId().isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        DeviceEntity device = support.requireDevice(deviceCode);
        CatalogFormSupport.requireEnabled(device.getEnabled(), "设备已停用: " + device.getDeviceCode());
        ProductFunctionEntity function = store.findFunction(device.getProductId(), request.functionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "功能未配置到产品: " + device.getProductId() + "/" + request.functionId()));
        CatalogFormSupport.requireFunctionPermission(function);
        DeviceEndpointBinding endpoint = support.requireEndpointForFunction(device, function);

        Map<String, Object> caller = request.arguments() == null ? Map.of() : request.arguments();

        PayloadDefinitionResolver.Definition definition = PayloadDefinitionResolver.resolve(function,
                store.properties());
        Map<String, Object> legacyOverrides = PropertySchemas.toValueMap(
                store.loadDeviceOverrides(device, function.getFunctionId()));
        Map<String, Object> pathOverrides = store.properties().listDeviceFieldOverrides(
                device.getId(), function.getFunctionId());
        Map<String, Object> deviceFieldOverrides = new LinkedHashMap<>(legacyOverrides);
        deviceFieldOverrides.putAll(pathOverrides);
        support.validateFieldOverrides(function, deviceFieldOverrides);

        Map<String, Object> payload = PayloadDefinitionResolver.assemble(definition, caller, deviceFieldOverrides);
        support.validateCommandPayload(function, payload);

        Map<String, Object> deliveryHints = new LinkedHashMap<>();
        if (function.getAccessType() != null && !function.getAccessType().isBlank()) {
            deliveryHints.put("accessType", function.getAccessType());
        }
        if ("MQTT".equalsIgnoreCase(function.getCapabilityType())) {
            TopicCatalog catalog = TopicCatalog.fromAddressMap(endpoint.address().values());
            Map<String, String> topicOverrides = store.properties().listDeviceTopicOverrides(
                    device.getId(), function.getFunctionId());
            support.validateTopicOverrides(function, endpoint, topicOverrides);
            var resolved = TopicRouteResolver.resolve(
                    catalog,
                    definition.route(),
                    topicOverrides,
                    !"READ".equalsIgnoreCase(function.getAccessType()));
            if (resolved.publishTopic() != null) {
                deliveryHints.put(TopicRouteResolver.MQTT_PUBLISH_TOPIC_HINT, resolved.publishTopic());
            }
            if ("READ".equalsIgnoreCase(function.getAccessType())) {
                deliveryHints.put("mqtt.subscribeOnly", "true");
                if (resolved.subscribeTopic() != null) {
                    deliveryHints.put(TopicRouteResolver.MQTT_SUBSCRIBE_TOPIC_HINT, resolved.subscribeTopic());
                }
            }
        }

        return FunctionCommand.of(
                device.getDeviceCode(),
                function.getFunctionId(),
                payload,
                deliveryHints);
    }
}
