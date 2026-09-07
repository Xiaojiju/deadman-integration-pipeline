package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.ValueOption;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFieldAdapterTest {

    @Test
    void dottedNumericPathBecomesArrayAndMappedOptionsBecomeMappings() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("devId").source("device").build(),
                WriteFieldOption.builder("devPsw").ignoreRequest(true).source("constant").constant("0").build(),
                WriteFieldOption.builder("params.0")
                        .ignoreRequest(true)
                        .options(List.of(new ValueOption("open", "1", "开门", "string", "string", false)))
                        .source("mapped")
                        .build());

        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        FieldNode params = root.children().stream()
                .filter(child -> "params".equals(child.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("array", params.type());
        assertEquals("0", params.children().get(0).name());
        assertEquals(FieldSource.MAPPED, params.children().get(0).source());

        FieldNode password = root.children().stream()
                .filter(child -> "devPsw".equals(child.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(FieldSource.CONSTANT, password.source());
        assertEquals("0", password.constant());

        List<ValueMapping> mappings = LegacyFieldAdapter.fromFieldAndValueOptions(fields, List.of());
        assertEquals(1, mappings.size());
        assertEquals("1", mappings.get(0).mappingValue());
        assertEquals("value", mappings.get(0).callerField());
        assertEquals("params.0", mappings.get(0).patches().get(0).path());
        assertEquals("open", mappings.get(0).patches().get(0).value());

        List<WriteFieldOption> roundTrip = LegacyFieldAdapter.toWriteFields(root);
        assertTrue(roundTrip.stream().anyMatch(field -> "params.0".equals(field.field())));
        assertTrue(roundTrip.stream().anyMatch(field -> "constant".equals(field.source()) && "0".equals(field.constant())));
    }

    @Test
    void roundTripsByteLengthAndByteOrder() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("offset")
                        .accessDataType("int")
                        .transformDataType("int")
                        .source("caller")
                        .byteLength(2)
                        .byteOrder("little")
                        .build());
        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        assertEquals(2, root.children().get(0).byteLength());
        assertEquals("little", root.children().get(0).byteOrder());
        List<WriteFieldOption> roundTrip = LegacyFieldAdapter.toWriteFields(root);
        assertEquals(2, roundTrip.get(0).byteLength());
        assertEquals("little", roundTrip.get(0).byteOrder());
    }

    @Test
    void functionLevelValueOptionsPatchMappedPathNotCommand() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("at")
                        .ignoreRequest(true)
                        .valueGenerator("timestamp_seconds")
                        .source("platform")
                        .build(),
                WriteFieldOption.builder("params.0")
                        .ignoreRequest(true)
                        .source("mapped")
                        .callerField("lock")
                        .build());
        List<ValueOption> options = List.of(
                new ValueOption("open", "1", "开门", "string", "string", false),
                new ValueOption("close", "0", "关门", "string", "string", false));
        List<ValueMapping> mappings = LegacyFieldAdapter.fromFieldAndValueOptions(fields, options);
        assertEquals(2, mappings.size());
        assertEquals("lock", mappings.get(0).callerField());
        assertEquals("params.0", mappings.get(0).patches().get(0).path());
        assertTrue(mappings.stream().noneMatch(item -> "command".equals(item.patches().get(0).path())));
    }

    @Test
    void constantFieldOptionsAreNotValueMappings() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("area")
                        .description("寄存器区")
                        .accessDataType("select")
                        .transformDataType("select")
                        .ignoreRequest(true)
                        .options(List.of(
                                new ValueOption("HOLDING", "HOLDING", "HOLDING", "string", "string", true),
                                new ValueOption("COIL", "COIL", "COIL", "string", "string", false)))
                        .source("constant")
                        .constant("HOLDING")
                        .build(),
                WriteFieldOption.builder("value")
                        .description("写入值")
                        .ignoreRequest(true)
                        .source("mapped")
                        .callerField("value")
                        .build());
        List<ValueOption> writeValueOptions = List.of(
                new ValueOption("1", "open", "开", "string", "string", false),
                new ValueOption("0", "close", "关", "string", "string", false));
        List<ValueMapping> mappings = LegacyFieldAdapter.fromFieldAndValueOptions(fields, writeValueOptions);
        assertEquals(2, mappings.size());
        assertEquals("open", mappings.get(0).mappingValue());
        assertEquals("1", mappings.get(0).patches().get(0).value());
        assertEquals("value", mappings.get(0).patches().get(0).path());
        assertTrue(mappings.stream().noneMatch(item -> "area".equals(item.patches().get(0).path())));
    }

    @Test
    void writeValueOptionsWinOverMappedFieldOptions() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("value")
                        .description("写入值")
                        .ignoreRequest(true)
                        .options(List.of(new ValueOption("HOLDING", "HOLDING", "HOLDING", "string", "string", false)))
                        .source("mapped")
                        .callerField("lock")
                        .build());
        List<ValueOption> writeValueOptions = List.of(
                new ValueOption("1", "open", "开", "string", "string", false));
        List<ValueMapping> mappings = LegacyFieldAdapter.fromFieldAndValueOptions(fields, writeValueOptions);
        assertEquals(1, mappings.size());
        assertEquals("open", mappings.get(0).mappingValue());
        assertEquals("lock", mappings.get(0).callerField());
        assertEquals("1", mappings.get(0).patches().get(0).value());
    }

    @Test
    void callerFieldRoundTripsOnCallerLeaves() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("params.2")
                        .description("密码")
                        .source("caller")
                        .callerField("password")
                        .build());
        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        FieldNode params = root.children().stream()
                .filter(child -> "params".equals(child.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("password", params.children().get(0).callerField());
        List<WriteFieldOption> roundTrip = LegacyFieldAdapter.toWriteFields(root);
        assertEquals("password", roundTrip.get(0).callerField());
        assertEquals("caller", roundTrip.get(0).source());
    }

    @Test
    void fromWriteFieldsKeepsConfiguredOrderNotAlphabetical() {
        List<WriteFieldOption> fields = List.of(
                WriteFieldOption.builder("seq").ignoreRequest(true).valueGenerator("uuid").source("platform").build(),
                WriteFieldOption.builder("at")
                        .ignoreRequest(true)
                        .valueGenerator("timestamp_seconds")
                        .source("platform")
                        .build(),
                WriteFieldOption.builder("devId").ignoreRequest(true).source("device").build());

        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        assertEquals(List.of("seq", "at", "devId"), root.children().stream().map(FieldNode::name).toList());
    }
}
