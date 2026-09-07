package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.DeviceCommandRequest;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductFunctionEntity;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.catalog.DeviceBindingCatalog;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.FunctionCommand;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogFormServiceGuardTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogPropertyRepository properties;
    @Mock
    private CapabilityRegistrar registrar;
    @Mock
    private DeviceBindingCatalog bindings;

    private CatalogFormService forms;

    private DeviceEntity device;
    private ProductFunctionEntity mqttWrite;

    @BeforeEach
    void stub() {
        device = new DeviceEntity();
        device.setId("dev-1");
        device.setDeviceCode("lamp-1");
        device.setProductId("p1");
        device.setEnabled(true);

        mqttWrite = function("fn-mqtt", "pub.cmd", "WRITE", 2, "MQTT");

        forms = new CatalogFormService(store, registrar, bindings);
        when(store.resolveDevice("lamp-1")).thenReturn(Optional.of(device));
    }

    @Test
    void disabledDeviceIsRejected() {
        device.setEnabled(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null)));
        assertTrue(ex.getMessage().contains("停用"));
    }

    @Test
    void writeWithoutWritePermissionIsRejected() {
        mqttWrite.setAccessPermission(1);
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null)));
        assertTrue(ex.getMessage().contains("写权限"));
    }

    @Test
    void picksMqttEndpointNotTheFirstModbusOne() {
        stubCommandAssembly(mqttWrite);
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MODBUS", "modbus-ch", Map.of("slaveId", 1)),
                endpoint("MQTT", "mqtt-ch", Map.of("default_pub", "dev/lamp/cmd"))));
        when(store.loadDeviceOverrides(device, "pub.cmd")).thenReturn(List.of());
        when(properties.listDeviceFieldOverrides("dev-1", "pub.cmd")).thenReturn(Map.of());
        when(properties.listDeviceTopicOverrides("dev-1", "pub.cmd")).thenReturn(Map.of());

        FunctionCommand command = forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null));
        assertEquals("dev/lamp/cmd", command.deliveryHints().get("mqtt.publishTopic").orElseThrow());
    }

    @Test
    void missingCapabilityEndpointIsRejected() {
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MODBUS", "modbus-ch", Map.of("slaveId", 1))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null)));
        assertTrue(ex.getMessage().contains("未绑定 MQTT"));
    }

    @Test
    void unknownFieldOverrideIsRejected() {
        stubCommandAssembly(mqttWrite);
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MQTT", "mqtt-ch", Map.of("default_pub", "dev/lamp/cmd"))));
        when(store.loadDeviceOverrides(device, "pub.cmd")).thenReturn(List.of());
        when(store.loadFunctionProperties(mqttWrite)).thenReturn(List.of());
        when(properties.listDeviceFieldOverrides("dev-1", "pub.cmd")).thenReturn(Map.of("hack", 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null)));
        assertTrue(ex.getMessage().contains("契约"));
    }

    @Test
    void replaceRejectsUnknownTopicSlot() {
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MQTT", "mqtt-ch", Map.of("default_pub", "dev/lamp/cmd"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.replaceDeviceTopicOverrides("lamp-1", "pub.cmd", Map.of("not-a-slot", "x")));
        assertTrue(ex.getMessage().contains("slot"));
    }

    @Test
    void twoEnabledMqttEndpointsAreRejected() {
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MQTT", "mqtt-a", Map.of("default_pub", "a/cmd")),
                endpoint("MQTT", "mqtt-b", Map.of("default_pub", "b/cmd"))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null)));
        assertTrue(ex.getMessage().contains("多个 MQTT"));
    }

    @Test
    void disabledMatchingEndpointIsSkipped() {
        stubCommandAssembly(mqttWrite);
        when(store.findFunction("p1", "pub.cmd")).thenReturn(Optional.of(mqttWrite));
        when(bindings.findEndpoints("lamp-1")).thenReturn(List.of(
                endpoint("MQTT", "mqtt-dead", Map.of("default_pub", "dead/cmd"), false),
                endpoint("MQTT", "mqtt-ch", Map.of("default_pub", "dev/lamp/cmd"), true)));
        when(store.loadDeviceOverrides(device, "pub.cmd")).thenReturn(List.of());
        when(properties.listDeviceFieldOverrides("dev-1", "pub.cmd")).thenReturn(Map.of());
        when(properties.listDeviceTopicOverrides("dev-1", "pub.cmd")).thenReturn(Map.of());

        FunctionCommand command = forms.buildCommand("lamp-1", new DeviceCommandRequest("pub.cmd", Map.of(), null, null));
        assertEquals("dev/lamp/cmd", command.deliveryHints().get("mqtt.publishTopic").orElseThrow());
    }

    private void stubCommandAssembly(ProductFunctionEntity function) {
        when(store.properties()).thenReturn(properties);
        when(properties.listWriteFields(function.getId())).thenReturn(List.of(
                WriteFieldOption.builder("value").description("值").build()));
        when(properties.listWriteValueOptions(function.getId())).thenReturn(List.of());
    }

    private static ProductFunctionEntity function(
            String id, String functionId, String access, int permission, String capability) {
        ProductFunctionEntity entity = new ProductFunctionEntity();
        entity.setId(id);
        entity.setProductId("p1");
        entity.setFunctionId(functionId);
        entity.setAccessType(access);
        entity.setAccessPermission(permission);
        entity.setCapabilityType(capability);
        entity.setWriteAccessType("STRUCT");
        return entity;
    }

    private static DeviceEndpointBinding endpoint(String capability, String channelCode, Map<String, Object> address) {
        return endpoint(capability, channelCode, address, true);
    }

    private static DeviceEndpointBinding endpoint(
            String capability, String channelCode, Map<String, Object> address, boolean enabled) {
        return new DeviceEndpointBinding(
                "lamp-1", channelCode, capability, Attributes.empty(), Attributes.from(address), enabled);
    }
}
