package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void extractsArrayIndexPath() {
        Map<String, Object> json = Map.of("params", List.of("F123", "0", "ok"));
        assertEquals("F123", PayloadDisassembler.extractPath(json, "params.0"));
        assertEquals("0", PayloadDisassembler.extractPath(json, "params.1"));
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption("params.0", "设备编码", "string", "string", false, List.of(), "none", null),
                new WriteFieldOption("params.1", "成败", "string", "string", false, List.of(), "none", null));
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals("F123", points.get("params.0"));
        assertEquals("0", points.get("params.1"));
    }

    @Test
    void fallsBackToFullJsonWhenNoFields() {
        Map<String, Object> json = Map.of("a", 1);
        Map<String, Object> points = PayloadDisassembler.disassemble(json, List.of());
        assertEquals(1, points.get("a"));
    }

    @Test
    void skipsMissingFieldsAndDoesNotFallBack() {
        Map<String, Object> json = Map.of("temp", 25, "other", "x");
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption("temp", "温度", "int", "int", false, List.of(), "none", null),
                new WriteFieldOption("humidity", "湿度", "int", "int", false, List.of(), "none", null));
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals(1, points.size());
        assertEquals(25, points.get("temp"));
        assertTrue(PayloadDisassembler.disassemble(json, List.of(
                new WriteFieldOption("missing", "无", "string", "string", false, List.of(), "none", null)
        )).isEmpty());
    }

    @Test
    void mapsProtocolValueToNorthboundAndRenamesCallerField() {
        Map<String, Object> json = Map.of("params", List.of("F123", "0"));
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption(
                        "params.1",
                        "成败",
                        "string",
                        "string",
                        true,
                        List.of(new ValueOption("0", "failed", "失败", "string", "string", false),
                                new ValueOption("1", "ok", "成功", "string", "string", false)),
                        "none",
                        null,
                        "caller",
                        null,
                        "success"));
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals("failed", points.get("success"));
        assertEquals(1, points.size());
    }

    @Test
    void appliesFunctionScaleOnSingleValueField() {
        Map<String, Object> json = Map.of("value", 238);
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption("value", "温度", "int", "int", false, List.of(), "none", null));
        Map<String, Object> points = PayloadDisassembler.project(json, fields, List.of(), "divide", "10");
        assertEquals(23.8, ((Number) points.get("value")).doubleValue(), 0.0001);
    }
}
