package com.mtfm.gateway.app;

import com.mtfm.gateway.runtime.GatewayPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GatewayApplicationTest {

    @Autowired
    private GatewayPipeline pipeline;

    @Test
    void hostStartsWithPipelineWired() {
        assertNotNull(pipeline);
        assertNotNull(pipeline.find("PROTO").orElse(null));
        assertNotNull(pipeline.find("MODBUS").orElse(null));
        assertTrue(pipeline.list().stream().anyMatch(item -> "MQTT".equals(item.capabilityType())));
        assertTrue(pipeline.find("MQTT").orElseThrow().connectionSchema().stream()
                .anyMatch(field -> "host".equals(field.name()) && field.required()));
        assertTrue(pipeline.find("MODBUS").orElseThrow().addressSchema().stream()
                .anyMatch(field -> "slaveId".equals(field.name()) && field.required()));
    }
}
