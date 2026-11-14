package io.trellis.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL 17 for tests that need the platform tables (advisory locks, JSONB, ON CONFLICT). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {
    private static final String IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
