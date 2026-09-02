package com.mtfm.gateway.catalog.payload;

import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.FieldSource;
import com.mtfm.gateway.spi.payload.FunctionRoute;
import com.mtfm.gateway.spi.payload.PayloadEncoding;
import com.mtfm.gateway.spi.payload.PayloadMode;
import com.mtfm.gateway.spi.property.WriteFieldOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadDefinitionResolverHexTest {

    @Test
    void assemblePacksHexFrameAfterStructFill() {
        FieldNode root = FieldNode.objectRoot("root", List.of(
                FieldNode.leaf("area", "int", FieldSource.CALLER, "none", null, null, ""),
                FieldNode.leaf("func", "int", FieldSource.CALLER, "none", null, null, ""),
                FieldNode.leaf("offset", "int", FieldSource.CALLER, "none", null, null, ""),
                FieldNode.leaf("quantity", "int", FieldSource.CALLER, "none", null, null, "")));
        List<WriteFieldOption> fields = List.of(
                option("area", 1),
                option("func", 1),
                option("offset", 2),
                option("quantity", 2));
        PayloadDefinitionResolver.Definition definition = new PayloadDefinitionResolver.Definition(
                PayloadMode.STRUCT,
                root,
                List.of(),
                new FunctionRoute(null, null),
                PayloadEncoding.HEX,
                fields);
        Map<String, Object> packed = PayloadDefinitionResolver.assemble(
                definition,
                Map.of("area", 0, "func", 0, "offset", 0, "quantity", 1),
                Map.of());
        assertEquals("00 00 00 00 00 01", packed.get("_value"));
    }

    private static WriteFieldOption option(String name, int byteLength) {
        return new WriteFieldOption(
                name, "", "int", "int", false, List.of(), "none", null, "caller", null, null, byteLength, "big");
    }
}
