package com.mtfm.gateway.spi.payload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void valueModePatchesArrayIndexWithDeviceAndPlatform() {
        FieldNode params = new FieldNode(
                "params",
                "array",
                "none",
                FieldSource.CALLER,
                null,
                null,
                List.of(
                        FieldNode.leaf("0", "string", FieldSource.MAPPED, "none", null, null, "open|close"),
                        FieldNode.leaf("1", "string", FieldSource.DEVICE, "none", null, null, "phone"),
                        FieldNode.leaf("2", "string", FieldSource.CONSTANT, "none", null, "1", ""),
                        FieldNode.leaf("3", "string", FieldSource.DEVICE, "none", null, null, "lockSn"),
                        FieldNode.leaf("4", "string", FieldSource.DEVICE, "none", null, null, "mac"),
                        FieldNode.leaf("5", "string", FieldSource.DEVICE, "none", null, null, "pin")),
                FieldNode.leaf("item", "string", FieldSource.CALLER, "none", null, null, ""),
                List.of(),
                "");
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("devId", "string", FieldSource.DEVICE, "none", null, null, ""),
                FieldNode.leaf("devPsw", "string", FieldSource.CONSTANT, "none", null, "0", ""),
                FieldNode.leaf("at", "string", FieldSource.PLATFORM, "none", "timestamp_seconds", null, ""),
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "random_alnum_32", null, ""),
                FieldNode.leaf("op", "string", FieldSource.CONSTANT, "none", null, "opencloselock", ""),
                params));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("1", "开门", List.of(new FieldPatch("params.0", "open")))),
                Map.of("lock", 1),
                Map.of(
                        "devId", "35942218C174",
                        "params.1", "13812345678",
                        "params.3", "80448E15",
                        "params.4", "C0:62:80:66:11:EC",
                        "params.5", "12345678")));

        assertEquals("35942218C174", payload.get("devId"));
        assertEquals("0", payload.get("devPsw"));
        assertEquals("opencloselock", payload.get("op"));
        assertEquals(List.of("devId", "devPsw", "at", "seq", "op", "params"), List.copyOf(payload.keySet()));
        assertEquals(32, ((String) payload.get("seq")).length());
        assertTrue(payload.get("at") instanceof String);
        assertTrue(Long.parseLong((String) payload.get("at")) > 1_000_000_000L);
        assertEquals(
                List.of("open", "13812345678", "1", "80448E15", "C0:62:80:66:11:EC", "12345678"),
                payload.get("params"));
    }

    @Test
    void valueModeMultipleCallerFieldsPatchIndependently() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("op", "string", FieldSource.MAPPED, "none", null, null, ""),
                FieldNode.leaf("mode", "string", FieldSource.MAPPED, "none", null, null, "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(
                        ValueMapping.patch("lock", "1", "开门", List.of(new FieldPatch("op", "open"))),
                        ValueMapping.patch("lock", "0", "关门", List.of(new FieldPatch("op", "close"))),
                        ValueMapping.patch("mode", "night", "夜间", List.of(new FieldPatch("mode", "night")))),
                Map.of("lock", 1, "mode", "night"),
                Map.of()));

        assertEquals("open", payload.get("op"));
        assertEquals("night", payload.get("mode"));
    }

    @Test
    void valueModeAcceptsCommandLock() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("command", "string", FieldSource.MAPPED, "none", null, null, "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("0", "关门", List.of(new FieldPatch("command", "close")))),
                Map.of("command", Map.of("lock", 0)),
                Map.of()));

        assertEquals("close", payload.get("command"));
    }

    @Test
    void valueModeWholeArrayPatchStillWorks() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("params", "array", FieldSource.MAPPED, "none", null, null, "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("1", "开门", List.of(
                        new FieldPatch("params", List.of("open", "13812345678"))))),
                Map.of("value", "1"),
                Map.of()));

        assertEquals(List.of("open", "13812345678"), payload.get("params"));
    }

    @Test
    void constantJsonArrayStringBecomesList() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf(
                        "params",
                        "array",
                        FieldSource.CONSTANT,
                        "none",
                        null,
                        "[\"open\",\"13812345678\"]",
                        "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of(),
                Map.of()));

        assertEquals(List.of("open", "13812345678"), payload.get("params"));
    }

    @Test
    void platformTimestampFollowsFieldType() {
        FieldNode stringRoot = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("at", "string", FieldSource.PLATFORM, "none", "timestamp_seconds", null, "")));
        Object stringAt = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT, stringRoot, List.of(), Map.of(), Map.of())).get("at");
        assertTrue(stringAt instanceof String);
        Long.parseLong((String) stringAt);

        FieldNode intRoot = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("at", "int", FieldSource.PLATFORM, "none", "timestamp_seconds", null, "")));
        Object intAt = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT, intRoot, List.of(), Map.of(), Map.of())).get("at");
        assertTrue(intAt instanceof Long);

        FieldNode millisStringRoot = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("at", "string", FieldSource.PLATFORM, "none", "timestamp_millis", null, "")));
        Object millisAt = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT, millisStringRoot, List.of(), Map.of(), Map.of())).get("at");
        assertTrue(millisAt instanceof String);
        assertTrue(Long.parseLong((String) millisAt) > 1_000_000_000_000L);
    }

    @Test
    void payloadKeyOrderFollowsFieldTreeNotValueSource() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("devId", "string", FieldSource.DEVICE, "none", null, null, ""),
                FieldNode.leaf("at", "string", FieldSource.PLATFORM, "none", "timestamp_seconds", null, ""),
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "random_alnum_32", null, ""),
                FieldNode.leaf("op", "string", FieldSource.CONSTANT, "none", null, "opencloselock", ""),
                FieldNode.leaf("command", "string", FieldSource.MAPPED, "none", null, null, "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("1", "开门", List.of(new FieldPatch("command", "open")))),
                Map.of("value", "1"),
                Map.of("devId", "35942218C174")));

        assertEquals(List.of("devId", "at", "seq", "op", "command"), List.copyOf(payload.keySet()));
        assertEquals("35942218C174", payload.get("devId"));
        assertEquals("opencloselock", payload.get("op"));
        assertEquals("open", payload.get("command"));
    }

    @Test
    void valueModeCallerFieldsAcceptFreeFormInput() {
        FieldNode params = new FieldNode(
                "params",
                "array",
                "none",
                FieldSource.CALLER,
                null,
                null,
                List.of(
                        FieldNode.leaf("0", "string", FieldSource.CONSTANT, "none", null, "add", ""),
                        FieldNode.leaf("1", "string", FieldSource.CALLER, "none", null, null, "phone"),
                        new FieldNode(
                                "2", "string", "none", FieldSource.CALLER, null, null, List.of(), null, List.of(),
                                "password", null, null, "password"),
                        new FieldNode(
                                "3", "string", "none", FieldSource.CALLER, null, null, List.of(), null, List.of(),
                                "begin", null, null, "beginTime"),
                        new FieldNode(
                                "4", "string", "none", FieldSource.CALLER, null, null, List.of(), null, List.of(),
                                "expire", null, null, "expireTime"),
                        FieldNode.leaf("5", "string", FieldSource.DEVICE, "none", null, null, "lockSn"),
                        FieldNode.leaf("6", "string", FieldSource.CONSTANT, "none", null, "12345678", "")),
                FieldNode.leaf("item", "string", FieldSource.CALLER, "none", null, null, ""),
                List.of(),
                "");
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("devId", "string", FieldSource.DEVICE, "none", null, null, ""),
                FieldNode.leaf("devPsw", "string", FieldSource.CONSTANT, "none", null, "0", ""),
                FieldNode.leaf("at", "string", FieldSource.PLATFORM, "none", "timestamp_seconds", null, ""),
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "random_alnum_32", null, ""),
                FieldNode.leaf("op", "string", FieldSource.CONSTANT, "none", null, "password", ""),
                params));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(),
                Map.of(
                        "params.1", "13812345678",
                        "password", "112233",
                        "beginTime", "2024-08-01 19:20:15",
                        "expireTime", "2025-08-02 19:20:15"),
                Map.of("devId", "35942218C174", "params.5", "80448E15")));

        assertEquals("password", payload.get("op"));
        assertEquals(
                List.of(
                        "add",
                        "13812345678",
                        "112233",
                        "2024-08-01 19:20:15",
                        "2025-08-02 19:20:15",
                        "80448E15",
                        "12345678"),
                payload.get("params"));
    }

    @Test
    void valueModeMappedValueKeepsContractConstants() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("area", "string", FieldSource.CONSTANT, "none", null, "COIL", ""),
                FieldNode.leaf("offset", "int", FieldSource.CONSTANT, "none", null, "10", ""),
                FieldNode.leaf("value", "string", FieldSource.MAPPED, "none", null, null, ""),
                FieldNode.leaf("dataType", "string", FieldSource.CONSTANT, "none", null, "BOOLEAN", "")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("on", "开灯", List.of(new FieldPatch("value", "true")))),
                Map.of("value", "on"),
                Map.of()));

        assertEquals("COIL", payload.get("area"));
        assertEquals("10", payload.get("offset"));
        assertEquals("true", payload.get("value"));
        assertEquals("BOOLEAN", payload.get("dataType"));
    }

    @Test
    void deviceOverrideReplacesContractConstantOffset() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("area", "string", FieldSource.CONSTANT, "none", null, "HOLDING", ""),
                FieldNode.leaf("offset", "int", FieldSource.CONSTANT, "none", null, "6", ""),
                FieldNode.leaf("value", "string", FieldSource.MAPPED, "none", null, null, ""),
                FieldNode.leaf("dataType", "string", FieldSource.CONSTANT, "none", null, "INT16", "")));

        Map<String, Object> inherited = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("open", "开", List.of(new FieldPatch("value", "1")))),
                Map.of("value", "open"),
                Map.of()));
        assertEquals("6", inherited.get("offset"));
        assertEquals("HOLDING", inherited.get("area"));

        Map<String, Object> overridden = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("open", "开", List.of(new FieldPatch("value", "1")))),
                Map.of("value", "open"),
                Map.of("offset", 11, "area", "COIL", "dataType", "BOOLEAN")));
        assertEquals(11, overridden.get("offset"));
        assertEquals("HOLDING", overridden.get("area"));
        assertEquals("INT16", overridden.get("dataType"));
        assertEquals("1", overridden.get("value"));
    }

    @Test
    void requestIdGeneratorWritesCommandRequestId() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("seq", "string", FieldSource.PLATFORM, "none", "request_id", null, "序列")));

        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of(),
                Map.of(),
                "req-open-1"));

        assertEquals("req-open-1", payload.get("seq"));
    }

    @Test
    void outboundScaleConvertsBusinessToDevice() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                new FieldNode(
                        "value",
                        "int",
                        "none",
                        FieldSource.CALLER,
                        null,
                        null,
                        List.of(),
                        null,
                        List.of(),
                        "",
                        null,
                        null,
                        null,
                        "divide",
                        "10")));
        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.STRUCT,
                root,
                List.of(),
                Map.of("value", 23.8),
                Map.of()));
        assertEquals(238L, payload.get("value"));
    }

    @Test
    void mappedEnumIsNotScaled() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                new FieldNode(
                        "value",
                        "int",
                        "none",
                        FieldSource.MAPPED,
                        null,
                        null,
                        List.of(),
                        null,
                        List.of(),
                        "",
                        null,
                        null,
                        "value",
                        "divide",
                        "10")));
        Map<String, Object> payload = CommandAssembler.assemble(new CommandAssembler.Request(
                PayloadMode.VALUE,
                root,
                List.of(ValueMapping.patch("cool", "制冷", List.of(new FieldPatch("value", "2")))),
                Map.of("value", "cool"),
                Map.of()));
        assertEquals("2", String.valueOf(payload.get("value")));
    }
}
