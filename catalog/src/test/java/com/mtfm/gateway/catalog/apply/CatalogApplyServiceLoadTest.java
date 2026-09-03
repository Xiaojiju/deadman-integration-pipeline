package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.port.DriverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogApplyServiceLoadTest {

    @Mock
    private CatalogStore store;
    @Mock
    private ObjectProvider<com.mtfm.gateway.catalog.schema.CatalogFormService> forms;
    @Mock
    private ObjectProvider<com.mtfm.gateway.spi.port.PipelineCommandPort> commandPort;
    @Mock
    private DriverRegistry registry;
    @Mock
    private FunctionExecutor mqttExecutor;
    @Mock
    private FunctionExecutor modbusExecutor;

    @Test
    void rejectsMixedCapabilities() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        when(registry.findExecutor(any())).thenReturn(Optional.empty());
        DeviceEntity device = new DeviceEntity();
        device.setDeviceCode("mix-1");
        device.setEnabled(true);
        when(store.resolveDevice("mix-1")).thenReturn(Optional.of(device));
        when(store.findEndpoints("mix-1")).thenReturn(List.of(
                new DeviceEndpointBinding("mix-1", "modbus-ch", "MODBUS", Attributes.empty(), Attributes.empty()),
                new DeviceEndpointBinding("mix-1", "mqtt-ch", "MQTT", Attributes.empty(), Attributes.empty())));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> apply.load("mix-1"));
        assertTrue(ex.getMessage().contains("多种南向能力"));
    }

    @Test
    void bindsOnlyEnabledChannelOfSingleCapability() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        when(registry.findExecutor("MQTT")).thenReturn(Optional.of(mqttExecutor));

        DeviceEntity device = new DeviceEntity();
        device.setDeviceCode("lamp-1");
        device.setEnabled(true);
        when(store.resolveDevice("lamp-1")).thenReturn(Optional.of(device));
        DeviceEndpointBinding live = new DeviceEndpointBinding(
                "lamp-1", "mqtt-live", "MQTT", Attributes.empty(), Attributes.empty(), true);
        DeviceEndpointBinding dead = new DeviceEndpointBinding(
                "lamp-1", "mqtt-dead", "MQTT", Attributes.empty(), Attributes.empty(), false);
        when(store.findEndpoints("lamp-1")).thenReturn(List.of(dead, live));

        apply.load("lamp-1");

        verify(registry).register("lamp-1", "MQTT");
        verify(mqttExecutor).bind(live);
        verify(mqttExecutor, never()).bind(dead);
    }

    @Test
    void unloadUnbindsEveryBoundCapability() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        when(registry.findExecutor("MQTT")).thenReturn(Optional.of(mqttExecutor));
        when(registry.findExecutor("MODBUS")).thenReturn(Optional.of(modbusExecutor));
        when(store.findEndpoints("mix-1")).thenReturn(List.of(
                new DeviceEndpointBinding("mix-1", "modbus-ch", "MODBUS", Attributes.empty(), Attributes.empty()),
                new DeviceEndpointBinding("mix-1", "mqtt-ch", "MQTT", Attributes.empty(), Attributes.empty())));

        apply.unload("mix-1");

        verify(mqttExecutor).unbind("mix-1");
        verify(modbusExecutor).unbind("mix-1");
        verify(registry).unregister("mix-1");
    }

    @Test
    void reloadAllContinuesAfterOneDeviceFails() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        when(registry.findExecutor(any())).thenReturn(Optional.empty());
        DeviceEntity bad = new DeviceEntity();
        bad.setDeviceCode("bad");
        bad.setEnabled(true);
        DeviceEntity good = new DeviceEntity();
        good.setDeviceCode("good");
        good.setEnabled(true);
        when(store.listEnabledDevices()).thenReturn(List.of(bad, good));
        when(store.resolveDevice("bad")).thenReturn(Optional.of(bad));
        when(store.findEndpoints("bad")).thenThrow(new IllegalStateException("端点引用了不存在的通道"));
        when(store.resolveDevice("good")).thenReturn(Optional.of(good));
        when(store.findEndpoints("good")).thenReturn(List.of(
                new DeviceEndpointBinding("good", "mqtt-live", "MQTT", Attributes.empty(), Attributes.empty(), true)));

        apply.reloadAll();

        verify(registry).register("good", "MQTT");
    }
}
