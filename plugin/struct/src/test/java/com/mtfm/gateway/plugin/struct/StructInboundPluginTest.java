package com.mtfm.gateway.plugin.struct;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructInboundPluginTest {

    @Test
    void fillsDefaultFields() {
        StructInboundPlugin plugin = new StructInboundPlugin();
        EnvelopeDraft draft = EnvelopeDraft.builder()
                .deviceId("dev-1")
                .functionId("fn.write")
                .payload(Map.of("struct", true))
                .build();
        assertTrue(plugin.support(draft));
        InboundApplyResult.Continue cont = assertInstanceOf(InboundApplyResult.Continue.class, plugin.apply(draft));
        assertTrue(cont.draft().payload().get("fields").isPresent());
    }
}
