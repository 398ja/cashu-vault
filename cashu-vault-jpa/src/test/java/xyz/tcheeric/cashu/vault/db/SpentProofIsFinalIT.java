package xyz.tcheeric.cashu.vault.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * cashu-vault#154: the database itself refuses to undo a spend.
 *
 * <p>The API no longer offers a way to delete or rewrite a SPENT proof, but a leaked database
 * credential, or anyone at a psql prompt, would bypass the API entirely. {@code V13} installs a
 * trigger that holds the invariant underneath it. These tests issue raw SQL, exactly as such a
 * caller would.
 *
 * <p>Runs against real PostgreSQL: the trigger is PL/pgSQL and has no H2 counterpart.
 */
@Testcontainers
@SpringBootTest
class SpentProofIsFinalIT {

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

  private JdbcTemplate jdbc;
  private UUID mintId;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    mintId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO t_mint (id, archived, created_at, updated_at, version) VALUES (?, false, ?, ?, 0)",
        mintId, now, now);
  }

  // Guards the suite: on H2 there is no trigger and every assertion below would be vacuous.
  @Test
  void runsAgainstPostgres() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
    }
  }

  // A direct UPDATE moving a SPENT row back to UNSPENT fails, and the row stays SPENT.
  @Test
  void updatingASpentProofBackToUnspentFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(() -> jdbc.update("UPDATE t_proof SET state = 'UNSPENT' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("spent proof is final");

    assertThat(stateOf(id)).isEqualTo("SPENT");
  }

  // A SPENT row cannot be moved to PENDING either, which would let a hold refund it.
  @Test
  void updatingASpentProofToPendingFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(() -> jdbc.update("UPDATE t_proof SET state = 'PENDING' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);

    assertThat(stateOf(id)).isEqualTo("SPENT");
  }

  // A SPENT row's secret cannot be swapped for another, which would free the original secret.
  @Test
  void changingTheSecretOfASpentProofFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE t_proof SET secret = ? WHERE id = ?", "other-" + id, id))
        .isInstanceOf(DataAccessException.class);
  }

  // A direct DELETE of a SPENT row fails and the row survives.
  @Test
  void deletingASpentProofFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(() -> jdbc.update("DELETE FROM t_proof WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("spent proof is final");

    assertThat(stateOf(id)).isEqualTo("SPENT");
  }

  // TRUNCATE, which row triggers do not see, is refused too.
  @Test
  void truncatingTheProofTableFails() {
    insertProof("SPENT");

    assertThatThrownBy(() -> jdbc.execute("TRUNCATE t_proof"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("cannot be truncated");
  }

  // Bookkeeping on a SPENT row, such as archiving it, still works.
  @Test
  void archivingASpentProofIsAllowed() {
    UUID id = insertProof("SPENT");

    jdbc.update("UPDATE t_proof SET archived = true, version = version + 1 WHERE id = ?", id);

    assertThat(jdbc.queryForObject("SELECT archived FROM t_proof WHERE id = ?", Boolean.class, id))
        .isTrue();
  }

  // Rows that are not SPENT keep their normal lifecycle: they can move to SPENT, and back from
  // PENDING to UNSPENT on a refund.
  @Test
  void unspentAndPendingProofsStillTransition() {
    UUID pending = insertProof("PENDING");
    UUID unspent = insertProof("UNSPENT");

    jdbc.update("UPDATE t_proof SET state = 'UNSPENT' WHERE id = ?", pending);
    jdbc.update("UPDATE t_proof SET state = 'SPENT' WHERE id = ?", unspent);

    assertThat(stateOf(pending)).isEqualTo("UNSPENT");
    assertThat(stateOf(unspent)).isEqualTo("SPENT");
  }

  // A proof that was never spent can still be deleted at the database level, so an operator can
  // clean up a stuck row; only SPENT is final.
  @Test
  void deletingAnUnspentProofIsAllowedAtTheDatabase() {
    UUID id = insertProof("UNSPENT");

    jdbc.update("DELETE FROM t_proof WHERE id = ?", id);

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_proof WHERE id = ?", Integer.class, id))
        .isZero();
  }

  private UUID insertProof(String state) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO t_proof (id, archived, created_at, updated_at, version, mint_id, amount, "
            + "secret, c, state) VALUES (?, false, ?, ?, 0, ?, 1, ?, ?, ?)",
        id, now, now, mintId, "secret-" + id, "c-" + id, state);
    return id;
  }

  private String stateOf(UUID id) {
    return jdbc.queryForObject("SELECT state FROM t_proof WHERE id = ?", String.class, id);
  }
}
