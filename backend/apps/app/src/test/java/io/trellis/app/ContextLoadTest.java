package io.trellis.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application starts against a real database: every auto-configuration, Liquibase changeset and
 * startup verifier (permission catalog, feature toggles, issuer guard) passes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
class ContextLoadTest {
    @Test
    void contextLoads() {
        // Startup is the assertion.
    }
}
