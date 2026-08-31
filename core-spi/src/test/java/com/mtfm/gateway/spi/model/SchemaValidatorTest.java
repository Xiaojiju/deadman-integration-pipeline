package com.mtfm.gateway.spi.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    @Test
    void 缺少必填字段则失败() {
        List<SchemaField> schema = List.of(
                SchemaField.required("host", "string", "主机"),
                SchemaField.optional("port", "int", "端口", 1883)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SchemaValidator.require(schema, Map.of("port", 1883), "通道 connection"));
        assertTrue(ex.getMessage().contains("host"));
    }

    @Test
    void 必填齐全则通过() {
        List<SchemaField> schema = List.of(SchemaField.required("slaveId", "int", "从站号"));
        assertDoesNotThrow(() -> SchemaValidator.require(schema, Map.of("slaveId", 1), "端点 address"));
    }
}
