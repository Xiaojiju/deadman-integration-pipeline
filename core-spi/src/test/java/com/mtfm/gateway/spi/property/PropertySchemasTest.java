package com.mtfm.gateway.spi.property;

import com.mtfm.gateway.spi.model.SchemaField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySchemasTest {

    @Test
    void choices转WriteValueOption并标记默认项() {
        SchemaField field = SchemaField.choice("command", "控门指令", true, "open", List.of("open", "close"));
        List<ValueOption> options = PropertySchemas.choicesToValueOptions(field);
        assertEquals(2, options.size());
        assertEquals("open", options.get(0).optionValue());
        assertTrue(options.get(0).isDefault());
        assertEquals("close", options.get(1).optionValue());
        assertFalse(options.get(1).isDefault());
    }

    @Test
    void choiceOptionsByField只收集有枚举的字段() {
        List<SchemaField> fields = List.of(
                SchemaField.required("target", "string", "门号"),
                SchemaField.choice("command", "控门指令", true, "open", List.of("open", "close")));
        Map<String, List<ValueOption>> byField = PropertySchemas.choiceOptionsByField(fields);
        assertEquals(1, byField.size());
        assertTrue(byField.containsKey("command"));
        assertEquals(2, byField.get("command").size());
    }
}
