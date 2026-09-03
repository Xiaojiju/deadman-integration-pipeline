package com.mtfm.gateway.app;

import com.mtfm.gateway.runtime.GatewayPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway_app;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "gateway.mqtt.transport=memory",
        "gateway.modbus.mode=memory"
})
class GatewayApplicationTest {

    @Autowired
    private GatewayPipeline pipeline;

    @Autowired
    private DataSource dataSource;

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
        assertTrue(pipeline.find("MODBUS").orElseThrow().contractedParameters());
        assertTrue(pipeline.find("MODBUS").orElseThrow().connectionSchema().stream()
                .anyMatch(field -> "transport".equals(field.name())
                        && field.choices().contains("TCP")
                        && field.choices().contains("RTU")));
    }

    @Test
    void flywayMigratesCatalogTablesOnStartup() throws Exception {
        try (Connection connection = dataSource.getConnection();
                ResultSet tables = connection.getMetaData().getTables(null, null, "gw_product", null)) {
            assertTrue(tables.next(), "Flyway 应在启动时迁出 gw_product");
        }
    }
}
