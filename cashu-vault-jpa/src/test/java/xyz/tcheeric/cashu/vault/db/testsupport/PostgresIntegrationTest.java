package xyz.tcheeric.cashu.vault.db.testsupport;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Testcontainers-PostgreSQL-backed integration tests (Constitution IV).
 *
 * The container is started once per JVM in a static initializer and reused across every
 * test class that extends this base (a single shared PG instance reused across classes
 * avoids per-class 7s boot cost on the Failsafe fork). {@code @ServiceConnection} wires
 * Spring's DataSource to it; subclasses don't need {@code spring.datasource.*} overrides.
 *
 * Security is enabled — use {@code .with(httpBasic(...))} on MockMvc requests to authenticate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-pg")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("cashu_vault_it")
                .withUsername("cashu")
                .withPassword("cashu");
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }
}
