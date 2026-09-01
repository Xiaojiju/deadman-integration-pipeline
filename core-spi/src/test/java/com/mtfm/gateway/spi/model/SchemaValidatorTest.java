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
}
