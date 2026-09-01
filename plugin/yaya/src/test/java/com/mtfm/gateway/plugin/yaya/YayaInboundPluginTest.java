package com.mtfm.gateway.plugin.yaya;

import com.mtfm.gateway.spi.model.EnvelopeDraft;
import com.mtfm.gateway.spi.model.InboundApplyResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YayaInboundPluginTest {

    @Test
    void onlyPrefixedDeviceEntersAndMapsCmd() {
        YayaInboundPlugin plugin = new YayaInboundPlugin();
        EnvelopeDraft yaya = EnvelopeDraft.builder()
                .deviceId("yaya-1")
                .functionId("fn.open")
                .payload(Map.of("cmd", "on"))
                .build();
        EnvelopeDraft other = EnvelopeDraft.builder()
                .deviceId("dev-1")
                .functionId("fn.open")
                .payload(Map.of("cmd", "on"))
                .build();
        assertTrue(plugin.support(yaya));
        assertFalse(plugin.support(other));
        InboundApplyResult.Continue cont = assertInstanceOf(InboundApplyResult.Continue.class, plugin.apply(yaya));
        assertTrue(cont.draft().payload().get("action").isPresent());
    }
}
