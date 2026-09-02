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
                new WriteFieldOption(
                        "devId", "", "string", "string", false, List.of(), "none", null, "device", null),
                new WriteFieldOption(
                        "devPsw", "", "string", "string", true, List.of(), "none", null, "constant", "0"),
                new WriteFieldOption(
                        "params.0",
                        "",
                        "string",
                        "string",
                        true,
                        List.of(new ValueOption("open", "1", "开门", "string", "string", false)),
                        "none",
                        null,
                        "mapped",
                        null));

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
                new WriteFieldOption(
                        "offset", "", "int", "int", false, List.of(), "none", null, "caller", null, null, 2, "little"));
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
                new WriteFieldOption(
                        "at", "", "string", "string", true, List.of(), "none", "timestamp_seconds", "platform", null),
                new WriteFieldOption(
                        "params.0",
                        "",
                        "string",
                        "string",
                        true,
                        List.of(),
                        "none",
                        null,
                        "mapped",
                        null,
                        "lock"));
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
    void fromWriteFieldsKeepsConfiguredOrderNotAlphabetical() {
        List<WriteFieldOption> fields = List.of(
                new WriteFieldOption("seq", "", "string", "string", true, List.of(), "none", "uuid", "platform", null),
                new WriteFieldOption("at", "", "string", "string", true, List.of(), "none", "timestamp_seconds", "platform", null),
                new WriteFieldOption("devId", "", "string", "string", true, List.of(), "none", null, "device", null));

        FieldNode root = LegacyFieldAdapter.fromWriteFields(fields);
        assertEquals(List.of("seq", "at", "devId"), root.children().stream().map(FieldNode::name).toList());
    }
}
