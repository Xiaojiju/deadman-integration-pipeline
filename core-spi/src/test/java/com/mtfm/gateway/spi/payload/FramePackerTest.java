package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FramePackerTest {

    @Test
    void packsModbusLikeHexFrameInFieldOrder() {
        List<WriteFieldOption> fields = List.of(
                field("area", 1),
                field("func", 1),
                field("offset", 2),
                field("quantity", 2));
        Map<String, Object> packed = FramePacker.pack(
                fields,
                Map.of("area", 0, "func", 0, "offset", 0, "quantity", 1),
                PayloadEncoding.HEX);
        assertEquals("00 00 00 00 00 01", packed.get("_value"));
    }

    @Test
    void jsonEncodingReturnsOriginalMap() {
        Map<String, Object> values = Map.of("command", "open");
        assertSame(values, FramePacker.pack(List.of(), values, PayloadEncoding.JSON));
    }

    @Test
    void fillRootHexNormalizesSpaces() {
        Map<String, Object> packed = FramePacker.pack(
                List.of(),
                Map.of("_value", "00 00 00 00 00 01"),
                PayloadEncoding.HEX);
        assertEquals("00 00 00 00 00 01", packed.get("_value"));
    }

    @Test
    void valueMappingOnlyChangesOneFieldThenPacks() {
        List<WriteFieldOption> fields = List.of(
                field("func", 1),
                field("quantity", 2));
        Map<String, Object> packed = FramePacker.pack(
                fields,
                Map.of("func", 1, "quantity", 1),
                PayloadEncoding.HEX);
        assertEquals("01 00 01", packed.get("_value"));
    }

    @Test
    void unpacksHexFrameBackToIntegers() {
        List<WriteFieldOption> fields = List.of(
                field("area", 1),
                field("func", 1),
                field("offset", 2),
                field("quantity", 2));
        Map<String, Object> points = FramePacker.unpack("00 00 00 00 00 01", fields, PayloadEncoding.HEX);
        assertEquals(0, points.get("area"));
        assertEquals(0, points.get("func"));
        assertEquals(0, points.get("offset"));
        assertEquals(1, points.get("quantity"));
    }

    @Test
    void littleEndianWritesLowByteFirst() {
        WriteFieldOption field = WriteFieldOption.builder("offset")
                .accessDataType("int")
                .transformDataType("int")
                .source("caller")
                .byteLength(2)
                .byteOrder("little")
                .build();
        Map<String, Object> packed = FramePacker.pack(List.of(field), Map.of("offset", 1), PayloadEncoding.HEX);
        assertEquals("01 00", packed.get("_value"));
    }

    private static WriteFieldOption field(String name, int byteLength) {
        return WriteFieldOption.builder(name)
                .accessDataType("int")
                .transformDataType("int")
                .source("caller")
                .byteLength(byteLength)
                .byteOrder("big")
                .build();
    }
}
