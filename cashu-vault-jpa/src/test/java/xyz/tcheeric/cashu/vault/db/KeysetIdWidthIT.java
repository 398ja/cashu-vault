package xyz.tcheeric.cashu.vault.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A NUT-02 v2 keyset id is the version byte {@code 01} followed by a SHA-256 digest rendered as
 * hex, so it occupies 66 characters against the 16 of a v1 id.
 *
 * <p>The column was originally sized for v1, so provisioning a mint whose keyset id was derived
 * under v2 aborted with "value too long for type character varying(16)", and the provisioning
 * outbox retried until it gave up.
 *
 * <p>This runs against real PostgreSQL rather than the H2 the other tests use: H2 in PostgreSQL
 * mode does not enforce {@code VARCHAR} length, so it accepts an over-long id and cannot observe
 * the failure at all.
 */
@Testcontainers
@SpringBootTest
class KeysetIdWidthIT {

  private static final String V2_KEYSET_ID = "01" + "a".repeat(64);
  private static final String V1_KEYSET_ID = "00e3372e61d05605";

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add(
        "spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Autowired private DataSource dataSource;

  // Ensures these tests really run on PostgreSQL. H2 does not enforce VARCHAR
  // length, so on H2 the over-long insert would silently succeed and the whole
  // suite would pass even with the widening migration removed.
  @Test
  void runsAgainstPostgres() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName())
          .as("this IT is meaningless unless it runs on PostgreSQL")
          .isEqualTo("PostgreSQL");
    }
  }

  // Ensures a full-length v2 keyset id survives a round trip untruncated, which
  // is the insert that previously aborted the whole provisioning outbox.
  @Test
  void storesAFullLengthV2KeysetId() {
    final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final UUID mintId = insertMint(jdbc);

    insertKeyset(jdbc, mintId, V2_KEYSET_ID);

    assertThat(
            jdbc.queryForObject(
                "SELECT key_set_id FROM t_keyset WHERE mint_id = ?", String.class, mintId))
        .as("a v2 keyset id must persist in full, with no truncation")
        .isEqualTo(V2_KEYSET_ID);
  }

  // Ensures widening the column did not cost us the ability to store the v1 ids
  // that existing keysets, and the tokens already signed by them, still use.
  @Test
  void stillStoresALegacyV1KeysetId() {
    final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final UUID mintId = insertMint(jdbc);

    insertKeyset(jdbc, mintId, V1_KEYSET_ID);

    assertThat(
            jdbc.queryForObject(
                "SELECT key_set_id FROM t_keyset WHERE mint_id = ?", String.class, mintId))
        .isEqualTo(V1_KEYSET_ID);
  }

  private UUID insertMint(final JdbcTemplate jdbc) {
    final UUID mintId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO t_mint (id, archived, created_at, updated_at, version) "
            + "VALUES (?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        mintId);
    return mintId;
  }

  private void insertKeyset(final JdbcTemplate jdbc, final UUID mintId, final String keySetId) {
    jdbc.update(
        "INSERT INTO t_keyset (id, archived, created_at, updated_at, version, key_set_id, unit,"
            + " mint_id) VALUES (?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, ?, ?, ?)",
        UUID.randomUUID(),
        keySetId,
        "sat",
        mintId);
  }
}
