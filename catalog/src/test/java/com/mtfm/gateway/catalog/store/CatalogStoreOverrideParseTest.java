package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.spi.property.PropertyItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStoreOverrideParseTest {

    @Test
    void 按functionId解析旧optionOverrides() {
        Map<String, Object> legacy = Map.of(
                "remoteControlDoor", Map.of("command", "open", "target", "1"));
        Map<String, List<PropertyItem>> parsed = CatalogStore.parseLegacyOverrides(legacy);
        assertTrue(parsed.containsKey("remoteControlDoor"));
        assertEquals(2, parsed.get("remoteControlDoor").size());
    }

    @Test
    void 扁平覆盖不归属到任意功能() {
        Map<String, List<PropertyItem>> parsed = CatalogStore.parseLegacyOverrides(Map.of("command", "open"));
        assertTrue(parsed.isEmpty());
    }
}
