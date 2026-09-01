package com.mtfm.gateway.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogH2MigrationTest {

    @Test
    void h2MigratesAndProjectsOneChannelTwoAddresses() throws Exception {
        String url = "jdbc:h2:mem:catalog_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/h2")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO gw_product (id, code, name, created_at, updated_at)
                    VALUES ('p1', 'pump', '泵', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_channel (id, code, capability_type, connection, enabled, created_at, updated_at)
                    VALUES ('c1', 'gw-1', 'MODBUS', '{"host":"10.0.0.1","port":502}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_device (id, device_code, product_id, name, enabled, created_at, updated_at)
                    VALUES ('d1', 'A', 'p1', '设备A', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_device (id, device_code, product_id, name, enabled, created_at, updated_at)
                    VALUES ('d2', 'B', 'p1', '设备B', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_device_endpoint (id, device_id, channel_id, address, created_at)
                    VALUES ('e1', 'd1', 'c1', '{"unitId":1}', CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_device_endpoint (id, device_id, channel_id, address, created_at)
                    VALUES ('e2', 'd2', 'c1', '{"unitId":2}', CURRENT_TIMESTAMP)
                    """);
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM gw_device_endpoint WHERE channel_id = 'c1'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'gw_write_value_option'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            statement.executeUpdate("""
                    INSERT INTO gw_product_function
                    (id, product_id, function_id, access_type, access_permission, capability_type, write_access_type, sort_index)
                    VALUES ('pf1', 'p1', 'remoteControlDoor', 'WRITE', 2, 'HIKVISION', 'VALUE', 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_write_value_option
                    (id, parent_id, description, option_value, mapping_value, access_data_type, transform_data_type, is_default)
                    VALUES ('wvo1', 'pf1', '开门', 'open', 'open', 'string', 'string', TRUE)
                    """);
            statement.executeUpdate("""
                    INSERT INTO gw_write_value_option
                    (id, parent_id, description, option_value, mapping_value, access_data_type, transform_data_type, is_default)
                    VALUES ('wvo2', 'pf1', '关门', 'close', 'close', 'string', 'string', FALSE)
                    """);
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM gw_write_value_option WHERE parent_id = 'pf1'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
