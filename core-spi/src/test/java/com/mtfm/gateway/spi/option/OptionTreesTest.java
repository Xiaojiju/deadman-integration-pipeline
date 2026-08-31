package com.mtfm.gateway.spi.option;

import com.mtfm.gateway.spi.model.AccessDataType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionTreesTest {

    @Test
    void 扁字符串读成标量() {
        Option option = OptionTrees.fromUnknown("1200");
        assertTrue(option.isScalar());
        assertEquals("1200", ((OptionValue.Scalar) option.value()).raw());
    }

    @Test
    void 对象与数组合并差异节点() {
        Map<String, Option> productFields = new LinkedHashMap<>();
        productFields.put("speed", Option.scalar("100"));
        productFields.put("mode", Option.scalar("auto"));
        Option product = new Option("产品", AccessDataType.OBJECT, null, true, null, Boolean.TRUE,
                OptionValue.object(productFields));

        Map<String, Option> overrideFields = new LinkedHashMap<>();
        overrideFields.put("speed", Option.scalar("80"));
        Option override = new Option(null, AccessDataType.OBJECT, null, false, null, Boolean.TRUE,
                OptionValue.object(overrideFields));

        Option merged = OptionTrees.merge(product, override);
        OptionValue.ObjectNode node = assertInstanceOf(OptionValue.ObjectNode.class, merged.value());
        assertEquals("80", ((OptionValue.Scalar) node.fields().get("speed").value()).raw());
        assertEquals("auto", ((OptionValue.Scalar) node.fields().get("mode").value()).raw());
    }

    @Test
    void 数组可序列化为多级节点() {
        Option array = OptionTrees.fromUnknown(List.of("a", Map.of("k", "v")));
        assertTrue(array.isArray());
        OptionValue.ArrayNode node = (OptionValue.ArrayNode) array.value();
        assertEquals(2, node.items().size());
        assertTrue(node.items().get(1).isObject());
    }

    @Test
    void 对象树可压成表单值() {
        Map<String, Option> fields = new LinkedHashMap<>();
        fields.put("host", Option.scalar("10.0.0.1"));
        fields.put("port", Option.scalar("502"));
        Option option = new Option(null, AccessDataType.OBJECT, null, false, null, Boolean.TRUE,
                OptionValue.object(fields));
        Map<String, Object> values = OptionTrees.toValueMap(option);
        assertEquals("10.0.0.1", values.get("host"));
        assertEquals("502", values.get("port"));
    }
}
