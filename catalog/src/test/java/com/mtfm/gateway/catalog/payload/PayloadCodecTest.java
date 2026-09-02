package com.mtfm.gateway.catalog.payload;

import com.mtfm.gateway.spi.payload.FieldNode;
import com.mtfm.gateway.spi.payload.FieldSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadCodecTest {

    @Test
    void readsLowercaseSourceAndRoundTrips() {
        String json = """
                {"name":"root","type":"object","format":"none","source":"caller","valueGenerator":null,\
                "constant":null,"children":[{"name":"devId","type":"string","format":"none","source":"constant",\
                "valueGenerator":null,"constant":"FFFA25101101","children":[],"element":null,"choices":[],\
                "description":"组号"},{"name":"params","type":"array","format":"none","source":"constant",\
                "valueGenerator":null,"constant":"[\\"open\\",\\"13812345678\\"]","children":[],"element":null,\
                "choices":[],"description":"参数列表"}],"element":null,"choices":[],"description":""}
                """;
        FieldNode root = PayloadCodec.readFieldNode(json);
        assertNotNull(root);
        assertEquals("root", root.name());
        assertEquals(FieldSource.CALLER, root.source());
        assertEquals(2, root.children().size());
        assertEquals(FieldSource.CONSTANT, root.children().get(0).source());
        assertEquals("FFFA25101101", root.children().get(0).constant());
        assertTrue(root.children().get(1).isArray());

        FieldNode again = PayloadCodec.readFieldNode(PayloadCodec.writeFieldNode(root));
        assertEquals("devId", again.children().get(0).name());
        assertEquals(FieldSource.CONSTANT, again.children().get(0).source());
    }
}
