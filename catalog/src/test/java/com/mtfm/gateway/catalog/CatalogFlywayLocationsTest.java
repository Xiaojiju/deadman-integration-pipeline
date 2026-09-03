package com.mtfm.gateway.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogFlywayLocationsTest {

    @Test
    void dialectFollowsJdbcUrl() {
        assertEquals("h2", CatalogConfiguration.dialectFromJdbcUrl(
                "jdbc:h2:mem:gateway;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        assertEquals("mysql", CatalogConfiguration.dialectFromJdbcUrl(
                "jdbc:mysql://127.0.0.1:3306/gateway"));
        assertEquals("postgresql", CatalogConfiguration.dialectFromJdbcUrl(
                "jdbc:postgresql://192.168.3.247:5432/integration-pipeline"));
    }

    @Test
    void unknownDialectIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> CatalogConfiguration.dialectFromJdbcUrl("jdbc:oracle:thin:@localhost:1521:orcl"));
    }
}
