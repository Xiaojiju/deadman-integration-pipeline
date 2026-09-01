package com.mtfm.gateway.spi.payload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandAssemblerTest {

    @Test
    void valueModePatchWithPlatformAndDeviceFields() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("deviceId", "string", FieldSource.DEVICE, "none", null, null, "厂商设备 ID"),
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "random_alnum_32", null, "序列"),
                FieldNode.leaf("at", "int", FieldSource.PLATFORM, "none", "timestamp_millis", null, "时间"),
                FieldNode.leaf("command", "string", FieldSource.MAPPED, "none", null, null, "指令")));

        List<ValueMapping> mappings = List.of(
                ValueMapping.patch("open", "开门", List.of(new FieldPatch("command", "open"))));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                mappings,
                Map.of("value", "open"),
                Map.of("deviceId", "MFG-A-001")));

        assertEquals("open", payload.get("command"));
        assertEquals("MFG-A-001", payload.get("deviceId"));
        assertEquals(32, ((String) payload.get("seq")).length());
        assertTrue(payload.get("at") instanceof Long);
    }

    @Test
    void structModeNestedObject() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                new FieldNode(
                        "user",
                        "object",
                        "none",
                        FieldSource.CALLER,
                        null,
                        null,
                        List.of(FieldNode.leaf("name", "string", FieldSource.CALLER, "none", null, null, "")),
                        null,
                        List.of(),
                        ""),
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "uuid", null, "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of("user.name", "alice"),
                Map.of()));

        assertEquals("alice", ((Map<?, ?>) payload.get("user")).get("name"));
        assertTrue(payload.get("seq") instanceof String);
    }

    @Test
    void valueModeFillRootString() {
        List<ValueMapping> mappings = List.of(
                ValueMapping.fillRoot("open", "开门", "open"));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                FieldNode.objectRoot("root", List.of()),
                mappings,
                Map.of("value", "open"),
                Map.of()));

        assertEquals("open", payload.get("_value"));
    }
}
