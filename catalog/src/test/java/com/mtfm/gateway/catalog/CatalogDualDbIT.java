package com.mtfm.gateway.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一套逻辑 schema 在 MySQL 与 PostgreSQL 上可迁移。无 Docker 时跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class CatalogDualDbIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void mysql与postgresql均可启动并写入() throws Exception {
        migrateAndProbe(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), "classpath:db/migration/mysql");
        migrateAndProbe(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(),
                "classpath:db/migration/postgresql");
    }

    private static void migrateAndProbe(String url, String user, String password, String location) throws Exception {
        Flyway.configure()
                .dataSource(url, user, password)
                .locations(location)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO gw_channel (id, code, capability_type, connection, enabled, created_at, updated_at)
                    VALUES ('c1', 'gw-1', 'MODBUS', '{"host":"10.0.0.1","port":502}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM gw_channel")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1);
            }
        }
    }
}
