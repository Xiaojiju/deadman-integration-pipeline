package com.mtfm.gateway.spi.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldTypeFormatTest {

    @Test
    void fieldTypeFromAcceptsAliasesAndFallsBack() {
        assertEquals(FieldType.STRING, FieldType.from(null));
        assertEquals(FieldType.STRING, FieldType.from(""));
        assertEquals(FieldType.STRING, FieldType.from("string"));
        assertEquals(FieldType.INT, FieldType.from("integer"));
        assertEquals(FieldType.INT, FieldType.from("INT"));
        assertEquals(FieldType.BOOLEAN, FieldType.from("bool"));
        assertEquals(FieldType.SELECT, FieldType.from("enum"));
        assertEquals(FieldType.PASSWORD, FieldType.from("secret"));
        assertEquals(FieldType.JSON, FieldType.from("json"));
        assertEquals(FieldType.ARRAY, FieldType.from("array"));
        assertEquals(FieldType.ARRAY, FieldType.from("list"));
        assertEquals(FieldType.STRING, FieldType.from("unknown-xyz"));
        assertEquals("int", FieldType.INT.code());
        assertEquals("array", FieldType.ARRAY.code());
    }

    @Test
    void fieldFormatFromAcceptsAliasesAndFallsBack() {
        assertEquals(FieldFormat.NONE, FieldFormat.from(null));
        assertEquals(FieldFormat.NONE, FieldFormat.from(""));
        assertEquals(FieldFormat.DATETIME_ISO8601, FieldFormat.from("datetime"));
        assertEquals(FieldFormat.DATETIME_ISO8601, FieldFormat.from("datetime_iso8601"));
        assertEquals(FieldFormat.IMAGE_BASE64, FieldFormat.from("image"));
        assertEquals(FieldFormat.TEXT_LIST, FieldFormat.from("list"));
        assertEquals(FieldFormat.NONE, FieldFormat.from("weird"));
        assertEquals("image_base64", FieldFormat.IMAGE_BASE64.code());
    }

    @Test
    void schemaFieldCarriesFormat() {
        SchemaField field = SchemaField.optional("imgStr", FieldType.STRING, "人脸", FieldFormat.IMAGE_BASE64);
        assertEquals(FieldType.STRING, field.type());
        assertEquals(FieldFormat.IMAGE_BASE64, field.format());
        assertEquals("string", field.type().code());
    }
}
