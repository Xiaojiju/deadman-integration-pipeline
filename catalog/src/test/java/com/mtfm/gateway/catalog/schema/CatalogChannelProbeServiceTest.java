package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.ChannelProbeRequestBody;
import com.mtfm.gateway.catalog.dto.ChannelProbeView;
import com.mtfm.gateway.catalog.dto.DeviceEndpointWriteRequest;
import com.mtfm.gateway.catalog.dto.DeviceWriteRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import com.mtfm.gateway.catalog.entity.ProductTypeEntity;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.capability.CapabilityRegistrar;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.presence.DevicePresencePort;
import com.mtfm.gateway.spi.probe.ChannelProbe;
import com.mtfm.gateway.spi.probe.ChannelProbeRegistry;
import com.mtfm.gateway.spi.probe.ChannelProbeResult;
import com.mtfm.gateway.spi.probe.DiscoveredDevice;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogChannelProbeServiceTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogDeviceCommands devices;
    @Mock
    private CatalogApplyService applyService;
    @Mock
    private ChannelProbeRegistry probes;
    @Mock
    private ChannelProbe probe;
    @Mock
    private DevicePresencePort presence;
    @Mock
    private CapabilityRegistrar registrar;

    private CatalogChannelProbeService service;

    @BeforeEach
    void setUp() {
        service = new CatalogChannelProbeService(
                store,
                new CatalogFormSupport(store, registrar),
                devices,
                applyService,
                probes,
                presence);
    }

    @Test
    void createsMissingDeviceWithoutLoading() {
        stubChannelAndProduct();
        when(probe.scan(any())).thenReturn(new ChannelProbeResult(List.of(discovered("EHOME-A", "门禁A", "1", true))));
        when(store.findDeviceByCode("EHOME-A")).thenReturn(Optional.empty());
        DeviceEntity created = device("d1", "EHOME-A", "门禁A");
        when(devices.createDevice(any())).thenReturn(created);

        ChannelProbeView view = service.probe("ch-1", new ChannelProbeRequestBody("p1"));

        assertEquals(1, view.created());
        assertEquals("created", view.items().get(0).action());
        ArgumentCaptor<DeviceWriteRequest> captor = ArgumentCaptor.forClass(DeviceWriteRequest.class);
        verify(devices).createDevice(captor.capture());
        assertEquals("EHOME-A", captor.getValue().deviceCode());
        assertEquals("门禁A", captor.getValue().name());
        verify(devices).createEndpoint(eq("EHOME-A"), any(DeviceEndpointWriteRequest.class));
        verify(applyService, never()).load(any());
        verify(presence).apply(any());
    }

    @Test
    void updatesSerialWithoutRenamingAndReloadsIfLoaded() {
        stubChannelAndProduct();
        when(probe.scan(any())).thenReturn(new ChannelProbeResult(List.of(discovered("EHOME-A", "新名称", "2", false))));
        DeviceEntity existing = device("d1", "EHOME-A", "旧名称");
        when(store.findDeviceByCode("EHOME-A")).thenReturn(Optional.of(existing));
        DeviceEndpointEntity endpoint = new DeviceEndpointEntity();
        endpoint.setId("ep-1");
        endpoint.setDeviceId("d1");
        endpoint.setChannelId("ch-1");
        when(store.findEndpoint("d1", "ch-1")).thenReturn(Optional.of(endpoint));
        when(store.loadEndpointProperties(endpoint)).thenReturn(List.of(
                PropertyItem.of("deviceSerialNo", "1")));
        when(applyService.isLoaded("EHOME-A")).thenReturn(true);

        ChannelProbeView view = service.probe("ch-1", new ChannelProbeRequestBody("p1"));

        assertEquals(1, view.serialUpdated());
        assertEquals("serialUpdated", view.items().get(0).action());
        assertEquals("旧名称", view.items().get(0).name());
        verify(devices).updateEndpoint(eq("ep-1"), any(DeviceEndpointWriteRequest.class));
        verify(devices, never()).createDevice(any());
        verify(applyService).unload("EHOME-A");
        verify(applyService).load("EHOME-A");
    }

    @Test
    void leavesNameAndSerialUnchanged() {
        stubChannelAndProduct();
        when(probe.scan(any())).thenReturn(new ChannelProbeResult(List.of(discovered("EHOME-A", "新名称", "1", true))));
        DeviceEntity existing = device("d1", "EHOME-A", "旧名称");
        when(store.findDeviceByCode("EHOME-A")).thenReturn(Optional.of(existing));
        DeviceEndpointEntity endpoint = new DeviceEndpointEntity();
        endpoint.setId("ep-1");
        endpoint.setDeviceId("d1");
        endpoint.setChannelId("ch-1");
        when(store.findEndpoint("d1", "ch-1")).thenReturn(Optional.of(endpoint));
        when(store.loadEndpointProperties(endpoint)).thenReturn(List.of(
                PropertyItem.of("deviceSerialNo", "1")));

        ChannelProbeView view = service.probe("ch-1", new ChannelProbeRequestBody("p1"));

        assertEquals(1, view.unchanged());
        assertEquals("unchanged", view.items().get(0).action());
        assertEquals("旧名称", view.items().get(0).name());
        verify(devices, never()).updateEndpoint(any(), any());
        verify(applyService, never()).unload(any());
        verify(applyService, never()).load(any());
    }

    @Test
    void rejectsNonAccessControlProduct() {
        ChannelEntity channel = channel();
        when(store.findChannel("ch-1")).thenReturn(Optional.of(channel));
        when(registrar.find("HIKVISION_ENTRANCE")).thenReturn(Optional.of(hikvisionDescriptor()));
        when(probes.find("HIKVISION_ENTRANCE")).thenReturn(Optional.of(probe));
        ProductEntity product = new ProductEntity();
        product.setId("p1");
        product.setProductTypeId("other");
        when(store.findProduct("p1")).thenReturn(Optional.of(product));
        ProductTypeEntity type = new ProductTypeEntity();
        type.setId("other");
        type.setCode("OTHER");
        when(store.findProductType("other")).thenReturn(Optional.of(type));

        assertThrows(IllegalArgumentException.class,
                () -> service.probe("ch-1", new ChannelProbeRequestBody("p1")));
        verify(probe, never()).scan(any());
    }

    private void stubChannelAndProduct() {
        ChannelEntity channel = channel();
        when(store.findChannel("ch-1")).thenReturn(Optional.of(channel));
        when(registrar.find("HIKVISION_ENTRANCE")).thenReturn(Optional.of(hikvisionDescriptor()));
        when(probes.find("HIKVISION_ENTRANCE")).thenReturn(Optional.of(probe));
        when(store.openedConnection(channel)).thenReturn(Attributes.from(Map.of("host", "10.0.0.1")));
        ProductEntity product = new ProductEntity();
        product.setId("p1");
        product.setProductTypeId("ACCESS_CONTROL");
        when(store.findProduct("p1")).thenReturn(Optional.of(product));
        ProductTypeEntity type = new ProductTypeEntity();
        type.setId("ACCESS_CONTROL");
        type.setCode(ProductTypeEntity.ACCESS_CONTROL);
        when(store.findProductType("ACCESS_CONTROL")).thenReturn(Optional.of(type));
        when(store.listFunctions("p1")).thenReturn(List.of());
    }

    private static ChannelEntity channel() {
        ChannelEntity channel = new ChannelEntity();
        channel.setId("ch-1");
        channel.setCode("hik-1");
        channel.setCapabilityType("HIKVISION_ENTRANCE");
        channel.setEnabled(true);
        return channel;
    }

    private static DeviceEntity device(String id, String code, String name) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(id);
        entity.setDeviceCode(code);
        entity.setName(name);
        entity.setEnabled(true);
        return entity;
    }

    private static DiscoveredDevice discovered(String code, String name, String serial, boolean online) {
        return new DiscoveredDevice(code, name, Attributes.from(Map.of("deviceSerialNo", serial)), online);
    }

    private static CapabilityDescriptor hikvisionDescriptor() {
        return new CapabilityDescriptor(
                "HIKVISION_ENTRANCE",
                List.of(),
                List.of(),
                List.of(),
                FunctionCatalogMode.FIXED,
                true);
    }
}
