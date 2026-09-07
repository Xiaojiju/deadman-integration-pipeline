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
import java.util.UUID;

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

        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId().trim();
        Map<String, Object> caller = request.arguments() == null ? Map.of() : request.arguments();

        PayloadDefinitionResolver.Definition definition = PayloadDefinitionResolver.resolve(function,
                store.properties());
        Map<String, Object> legacyOverrides = PropertySchemas.toValueMap(
                store.loadDeviceOverrides(device, function.getFunctionId()));
        Map<String, Object> pathOverrides = store.properties().listDeviceFieldOverrides(
                device.getId(), function.getFunctionId());
        Map<String, Object> deviceFieldOverrides = new LinkedHashMap<>(legacyOverrides);
        deviceFieldOverrides.putAll(pathOverrides);
        deviceFieldOverrides = support.stripLockedContractOverrides(deviceFieldOverrides);
        support.validateFieldOverrides(function, deviceFieldOverrides);

        Map<String, Object> payload = PayloadDefinitionResolver.assemble(
                definition, caller, deviceFieldOverrides, requestId);
        support.validateCommandPayload(function, payload);

        Map<String, Object> deliveryHints = new LinkedHashMap<>();
        if (request.source() != null && !request.source().isBlank()) {
            deliveryHints.put("source", request.source().trim());
        }
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
                if (resolved.publishTopic() == null) {
                    deliveryHints.put("mqtt.subscribeOnly", "true");
                }
                if (resolved.subscribeTopic() != null) {
                    deliveryHints.put(TopicRouteResolver.MQTT_SUBSCRIBE_TOPIC_HINT, resolved.subscribeTopic());
                }
            }
            putReplyHints(deliveryHints, function, catalog.withOverrides(topicOverrides));
        }

        return FunctionCommand.of(
                requestId,
                device.getDeviceCode(),
                function.getFunctionId(),
                payload,
                deliveryHints);
    }

    private static void putReplyHints(
            Map<String, Object> deliveryHints, ProductFunctionEntity function, TopicCatalog catalog) {
        String replySlot = function.getReplyTopicSlot();
        if (replySlot == null || replySlot.isBlank()) {
            return;
        }
        try {
            String replyTopic = catalog.resolveSubscribe(replySlot);
            deliveryHints.put(TopicRouteResolver.MQTT_REPLY_TOPIC_HINT, replyTopic);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("无法解析应答 topic slot: " + replySlot, ex);
        }
        if (function.getCorrelationPath() != null && !function.getCorrelationPath().isBlank()) {
            deliveryHints.put(TopicRouteResolver.MQTT_CORRELATION_PATH_HINT, function.getCorrelationPath().trim());
        }
        if (function.getCorrelationCommandPath() != null && !function.getCorrelationCommandPath().isBlank()) {
            deliveryHints.put(
                    TopicRouteResolver.MQTT_CORRELATION_COMMAND_PATH_HINT,
                    function.getCorrelationCommandPath().trim());
        }
        if (function.getReplyTimeoutMs() != null && function.getReplyTimeoutMs() > 0) {
            deliveryHints.put(TopicRouteResolver.MQTT_REPLY_TIMEOUT_MS_HINT, function.getReplyTimeoutMs());
        }
    }
}
