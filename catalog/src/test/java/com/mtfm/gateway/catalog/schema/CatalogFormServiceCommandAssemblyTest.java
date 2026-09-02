package com.mtfm.gateway.catalog.schema;

import com.mtfm.gateway.spi.payload.CommandAssembler;
import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.PayloadMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogFormServiceCommandAssemblyTest {

    @Test
    void mergesCallerArgsAndPlatformGenerators() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("command", "string", com.mtfm.gateway.spi.payload.FieldSource.CALLER, "none", null, null,
                        ""),
                FieldNode.leaf("deviceId", "string", com.mtfm.gateway.spi.payload.FieldSource.DEVICE, "none", null,
                        null, ""),
                FieldNode.leaf("seq", "string", com.mtfm.gateway.spi.payload.FieldSource.PLATFORM, "none",
                        "random_alnum_32", null, ""),
                FieldNode.leaf("at", "int", com.mtfm.gateway.spi.payload.FieldSource.PLATFORM, "none",
                        "timestamp_millis", null, "")));

        Map<String, Object> merged = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of("command", "open"),
                Map.of("deviceId", "MFG-A-001")));

        assertEquals("open", merged.get("command"));
        assertEquals("MFG-A-001", merged.get("deviceId"));
        assertEquals(32, ((String) merged.get("seq")).length());
        assertTrue(merged.get("at") instanceof Long);
    }

    @Test
    void ignoresCallerValueForPlatformField() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("seq", "string", com.mtfm.gateway.spi.payload.FieldSource.PLATFORM, "none",
                        "random_alnum_32", null, "")));

        Map<String, Object> merged = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of("seq", "caller-should-not-win"),
                Map.of()));

        assertFalse("caller-should-not-win".equals(merged.get("seq")));
        assertEquals(32, ((String) merged.get("seq")).length());
    }
}
