package com.mtfm.gateway.catalog.apply;

import com.mtfm.gateway.catalog.entity.ChannelEntity;
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

    @Test
    void rejectsMixedCapabilities() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        DeviceEntity device = new DeviceEntity();
        device.setDeviceCode("mix-1");
        device.setEnabled(true);
        when(store.resolveDevice("mix-1")).thenReturn(Optional.of(device));
        when(store.findDevice("mix-1")).thenReturn(Optional.empty());
        when(store.findEndpoints("mix-1")).thenReturn(List.of(
                new DeviceEndpointBinding("mix-1", "modbus-ch", "MODBUS", Attributes.empty(), Attributes.empty()),
                new DeviceEndpointBinding("mix-1", "mqtt-ch", "MQTT", Attributes.empty(), Attributes.empty())));
        when(store.findChannel("modbus-ch")).thenReturn(Optional.of(enabledChannel("modbus-ch")));
        when(store.findChannel("mqtt-ch")).thenReturn(Optional.of(enabledChannel("mqtt-ch")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> apply.load("mix-1"));
        assertTrue(ex.getMessage().contains("多种南向能力"));
    }

    @Test
    void bindsOnlyEnabledChannelOfSingleCapability() {
        CatalogApplyService apply = new CatalogApplyService(store, forms, commandPort);
        apply.attach(registry);
        when(mqttExecutor.capabilityType()).thenReturn("MQTT");
        apply.registerExecutor(mqttExecutor);

        DeviceEntity device = new DeviceEntity();
        device.setDeviceCode("lamp-1");
        device.setEnabled(true);
        when(store.resolveDevice("lamp-1")).thenReturn(Optional.of(device));
        when(store.findDevice("lamp-1")).thenReturn(Optional.empty());
        DeviceEndpointBinding live = new DeviceEndpointBinding(
                "lamp-1", "mqtt-live", "MQTT", Attributes.empty(), Attributes.empty());
        DeviceEndpointBinding dead = new DeviceEndpointBinding(
                "lamp-1", "mqtt-dead", "MQTT", Attributes.empty(), Attributes.empty());
        when(store.findEndpoints("lamp-1")).thenReturn(List.of(dead, live));
        ChannelEntity disabled = enabledChannel("mqtt-dead");
        disabled.setEnabled(false);
        when(store.findChannel("mqtt-dead")).thenReturn(Optional.of(disabled));
        when(store.findChannel("mqtt-live")).thenReturn(Optional.of(enabledChannel("mqtt-live")));

        apply.load("lamp-1");

        verify(registry).register("lamp-1", "MQTT");
        verify(mqttExecutor).bind(live);
        verify(mqttExecutor, never()).bind(dead);
    }

    private static ChannelEntity enabledChannel(String code) {
        ChannelEntity channel = new ChannelEntity();
        channel.setCode(code);
        channel.setEnabled(true);
        return channel;
    }
}
