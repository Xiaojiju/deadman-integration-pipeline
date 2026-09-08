package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.catalog.dto.DeviceEndpointPatchRequest;
import com.mtfm.gateway.catalog.dto.DeviceUpdateRequest;
import com.mtfm.gateway.catalog.entity.ChannelEntity;
import com.mtfm.gateway.catalog.entity.DeviceEndpointEntity;
import com.mtfm.gateway.catalog.entity.DeviceEntity;
import com.mtfm.gateway.catalog.store.CatalogActionRepository;
import com.mtfm.gateway.catalog.store.CatalogPropertyRepository;
import com.mtfm.gateway.catalog.store.CatalogStore;
import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FieldType;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.SchemaField;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogDeviceCommandsTest {

    @Mock
    private CatalogStore store;
    @Mock
    private CatalogFormSupport support;
    @Mock
    private CatalogActionRepository actions;
    @Mock
    private CatalogPropertyRepository properties;

    private CatalogDeviceCommands commands;

    @BeforeEach
    void setUp() {
        commands = new CatalogDeviceCommands(store, support, actions);
    }

    @Test
    void updateDeviceRenamesCodeAndRetargetsReferences() {
        DeviceEntity entity = device("d1", "old-code", "门禁");
        when(support.requireDevice("old-code")).thenReturn(entity);
        when(store.findDeviceByCode("new-code")).thenReturn(Optional.empty());
        when(store.updateDevice(entity)).thenReturn(entity);

        DeviceEntity saved = commands.updateDevice("old-code", new DeviceUpdateRequest(
                "一号门", "new-code", null, true, null));

        assertEquals("new-code", saved.getDeviceCode());
        assertEquals("一号门", saved.getName());
        verify(actions).retargetDeviceCode("old-code", "new-code");
    }

    @Test
    void updateDeviceRejectsDuplicateCode() {
        DeviceEntity entity = device("d1", "old-code", "门禁");
        DeviceEntity other = device("d2", "taken", "另一台");
        when(support.requireDevice("old-code")).thenReturn(entity);
        when(store.findDeviceByCode("taken")).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class, () -> commands.updateDevice(
                "old-code", new DeviceUpdateRequest(null, "taken", null, null, null)));
        verify(store, never()).updateDevice(any());
        verify(actions, never()).retargetDeviceCode(any(), any());
    }

    @Test
    void updateDevicePatchesEndpointAddress() {
        DeviceEntity entity = device("d1", "door-1", "门禁");
        DeviceEndpointEntity endpoint = new DeviceEndpointEntity();
        endpoint.setId("ep-1");
        endpoint.setDeviceId("d1");
        endpoint.setChannelId("ch-1");
        ChannelEntity channel = new ChannelEntity();
        channel.setId("ch-1");
        channel.setCapabilityType("HIKVISION_ISAPI");
        when(support.requireDevice("door-1")).thenReturn(entity);
        when(store.updateDevice(entity)).thenReturn(entity);
        when(store.findEndpoint("ep-1")).thenReturn(Optional.of(endpoint));
        when(store.findChannel("ch-1")).thenReturn(Optional.of(channel));
        when(store.properties()).thenReturn(properties);
        when(support.requireCapability("HIKVISION_ISAPI")).thenReturn(addressCapability(
                "HIKVISION_ISAPI", "deviceSerialNo"));

        List<PropertyItem> items = List.of(new PropertyItem("deviceSerialNo", "SN-2", "string", "序列号"));
        commands.updateDevice("door-1", new DeviceUpdateRequest(
                null, null, null, null, List.of(new DeviceEndpointPatchRequest("ep-1", items))));

        verify(properties).replaceEndpointProperties(eq("ep-1"), eq(items));
        verify(actions, never()).retargetDeviceCode(any(), any());
    }

    private static DeviceEntity device(String id, String code, String name) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(id);
        entity.setDeviceCode(code);
        entity.setName(name);
        entity.setEnabled(true);
        return entity;
    }

    private static CapabilityDescriptor addressCapability(String type, String fieldName) {
        return new CapabilityDescriptor(
                type,
                List.of(),
                List.of(new SchemaField(fieldName, FieldType.STRING, true, "序列号")),
                List.of(),
                FunctionCatalogMode.FIXED);
    }
}
