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
                WriteFieldOption.builder("temp").description("温度").accessDataType("int").transformDataType("int").build(),
                WriteFieldOption.builder("nested.door").description("门").build());
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
                WriteFieldOption.builder("params.0").description("设备编码").build(),
                WriteFieldOption.builder("params.1").description("成败").build());
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
                WriteFieldOption.builder("temp").description("温度").accessDataType("int").transformDataType("int").build(),
                WriteFieldOption.builder("humidity").description("湿度").accessDataType("int").transformDataType("int").build());
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals(1, points.size());
        assertEquals(25, points.get("temp"));
        assertTrue(PayloadDisassembler.disassemble(json, List.of(
                WriteFieldOption.builder("missing").description("无").build()
        )).isEmpty());
    }

    @Test
    void mapsProtocolValueToNorthboundAndRenamesCallerField() {
        Map<String, Object> json = Map.of("params", List.of("F123", "0"));
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("params.1")
                        .description("成败")
                        .ignoreRequest(true)
                        .options(List.of(
                                new ValueOption("0", "failed", "失败", "string", "string", false),
                                new ValueOption("1", "ok", "成功", "string", "string", false)))
                        .source("caller")
                        .callerField("success")
                        .build());
        Map<String, Object> points = PayloadDisassembler.disassemble(json, fields);
        assertEquals("failed", points.get("success"));
        assertEquals(1, points.size());
    }

    @Test
    void appliesFunctionScaleOnSingleValueField() {
        Map<String, Object> json = Map.of("value", 238);
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("value").description("温度").accessDataType("int").transformDataType("int").build());
        Map<String, Object> points = PayloadDisassembler.project(json, fields, List.of(), "divide", "10");
        assertEquals(23.8, ((Number) points.get("value")).doubleValue(), 0.0001);
    }
}
