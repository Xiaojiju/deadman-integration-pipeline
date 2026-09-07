package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.property.PropertyItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStoreOverrideParseTest {

    @Test
    void parsesLegacyOptionOverridesByFunctionId() {
        Map<String, Object> legacy = Map.of(
                "remoteControlDoor", Map.of("command", "open", "target", "1"));
        Map<String, List<PropertyItem>> parsed = CatalogEavLoader.parseLegacyOverrides(legacy);
        assertTrue(parsed.containsKey("remoteControlDoor"));
        assertEquals(2, parsed.get("remoteControlDoor").size());
    }

    @Test
    void flatOverrideDoesNotBelongToAnyFunction() {
        Map<String, List<PropertyItem>> parsed = CatalogEavLoader.parseLegacyOverrides(Map.of("command", "open"));
        assertTrue(parsed.isEmpty());
    }

    @Test
    void uniqueCapabilityTypeRejectsMixedEndpoints() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> CatalogBindingCatalog.uniqueCapabilityType(List.of(
                new DeviceEndpointBinding("mix-1", "modbus-ch", "MODBUS", Attributes.empty(), Attributes.empty()),
                new DeviceEndpointBinding("mix-1", "mqtt-ch", "MQTT", Attributes.empty(), Attributes.empty()))));
        assertTrue(ex.getMessage().contains("多种南向能力"));
    }

    @Test
    void uniqueCapabilityTypeReturnsTheOnlyType() {
        assertEquals("MQTT", CatalogBindingCatalog.uniqueCapabilityType(List.of(
                new DeviceEndpointBinding("lamp-1", "mqtt-a", "MQTT", Attributes.empty(), Attributes.empty()),
                new DeviceEndpointBinding("lamp-1", "mqtt-b", "MQTT", Attributes.empty(), Attributes.empty()))));
    }
}
