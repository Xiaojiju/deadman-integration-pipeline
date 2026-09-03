package com.mtfm.gateway.spi.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    @Test
    void failsWhenRequiredFieldMissing() {
        List<SchemaField> schema = List.of(
                SchemaField.required("host", FieldType.STRING, "主机"),
                SchemaField.optional("port", FieldType.INT, "端口", 1883)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("port", 1883), "通道 connection"));
        assertTrue(ex.getMessage().contains("host"));
    }

    @Test
    void passesWhenRequiredFieldsPresent() {
        List<SchemaField> schema = List.of(SchemaField.required("slaveId", FieldType.INT, "从站号"));
        assertDoesNotThrow(() -> SchemaValidator.require(schema, Map.of("slaveId", 1), "端点 address"));
    }

    @Test
    void rejectsUnknownFields() {
        List<SchemaField> schema = List.of(SchemaField.required("host", FieldType.STRING, "主机"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("host", "127.0.0.1", "extra", "x"), "通道 connection"));
        assertTrue(ex.getMessage().contains("extra"));
    }

    @Test
    void acceptsIntegerStringsAndRejectsNonIntegers() {
        List<SchemaField> schema = List.of(SchemaField.optional("port", FieldType.INT, "端口"));
        assertDoesNotThrow(() -> SchemaValidator.require(schema, Map.of("port", "502"), "通道 connection"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("port", "abc"), "通道 connection"));
        assertTrue(ex.getMessage().contains("port"));
    }

    @Test
    void acceptsBooleanAliases() {
        List<SchemaField> schema = List.of(SchemaField.optional("enabled", FieldType.BOOLEAN, "启用"));
        assertDoesNotThrow(() -> SchemaValidator.require(schema, Map.of("enabled", "true"), "通道 connection"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("enabled", "yes"), "通道 connection"));
        assertTrue(ex.getMessage().contains("enabled"));
    }

    @Test
    void rejectsIntegersOutsideDeclaredRange() {
        List<SchemaField> schema = List.of(SchemaField.optional("port", FieldType.INT, "端口", 502).range(1, 65535));
        assertDoesNotThrow(() -> SchemaValidator.require(schema, Map.of("port", 502), "通道 connection"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("port", 70000), "通道 connection"));
        assertTrue(ex.getMessage().contains("port"));
        assertTrue(ex.getMessage().contains("超出范围"));
    }

    @Test
    void requireConstraintsIgnoresUnknownKeys() {
        List<SchemaField> schema = List.of(SchemaField.required("offset", FieldType.INT, "起始地址", 0).range(0, 65535));
        assertDoesNotThrow(() -> SchemaValidator.requireConstraints(
                schema, Map.of("offset", 2, "packed", "FF"), "功能参数"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.requireConstraints(schema, Map.of("offset", -1), "功能参数"));
        assertTrue(ex.getMessage().contains("offset"));
    }
}
