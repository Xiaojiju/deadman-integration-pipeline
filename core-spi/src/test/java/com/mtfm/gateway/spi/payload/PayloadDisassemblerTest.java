package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadDisassemblerTest {

    @Test
    void extractsConfiguredReadFields() {
        Map<String, Object> json = Map.of(
                "temp", 25,
                "humidity", 60,
                "nested", Map.of("door", "open"));
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption("temp", "温度", "int", "int", false, List.of(), "none", null),
                new WriteFieldOption("nested.door", "门", "string", "string", false, List.of(), "none", null));
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals(25, points.get("temp"));
        assertEquals("open", points.get("nested.door"));
    }

    @Test
    void fallsBackToFullJsonWhenNoFields() {
        Map<String, Object> json = Map.of("a", 1);
        Map<String, Object> points = PayloadDisassembler.disassemble(json, List.of());
        assertEquals(1, points.get("a"));
    }
}
