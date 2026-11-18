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

  private static final String KEYSET = "00aaaaaaaaaaaaaa";

  @Autowired private DataSource dataSource;
  @Autowired private xyz.tcheeric.cashu.vault.db.repos.ProofRepository proofRepository;

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

  // cashu-vault#156: the books freeze. A rewritten amount on a spent row would shift the books.
  @Test
  void changingTheAmountOfASpentProofFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(() -> jdbc.update("UPDATE t_proof SET amount = 1024 WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("spent proof is final");

    assertThat(jdbc.queryForObject("SELECT amount FROM t_proof WHERE id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void changingTheKeysetOfASpentProofFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE t_proof SET keyset_id = '00ffffffffffffff' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void attributingALegacySpentProofToAKeysetFails() {
    UUID id = insertProof("SPENT", null);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE t_proof SET keyset_id = '00ffffffffffffff' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void changingTheSpendTimeOfASpentProofFails() {
    UUID id = insertProof("SPENT");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE t_proof SET spent_at = now() - interval '1 day' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("UPDATE t_proof SET spent_at = NULL WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
  }

  // Once set, a keyset id is immutable in every state, not only SPENT.
  @Test
  void changingTheKeysetOfAnUnspentProofFails() {
    UUID id = insertProof("UNSPENT");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE t_proof SET keyset_id = '00ffffffffffffff' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("keyset is immutable");
    assertThatThrownBy(() -> jdbc.update("UPDATE t_proof SET keyset_id = NULL WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);
  }

  // A legacy row without a keyset may still be given one while it is not spent.
  @Test
  void settingTheKeysetOfAnUnattributedUnspentProofIsAllowed() {
    UUID id = insertProof("UNSPENT", null);

    jdbc.update("UPDATE t_proof SET keyset_id = ? WHERE id = ?", KEYSET, id);

    assertThat(jdbc.queryForObject("SELECT keyset_id FROM t_proof WHERE id = ?", String.class, id))
        .isEqualTo(KEYSET);
  }

  // spent_at is the trigger's: a raw UPDATE into SPENT sets it, whatever the statement says.
  @Test
  void rawUpdateIntoSpentSetsSpentAt() {
    UUID id = insertProof("UNSPENT");

    jdbc.update(
        "UPDATE t_proof SET state = 'SPENT', spent_at = TIMESTAMPTZ '2020-01-01 00:00:00Z' WHERE id = ?",
        id);

    Timestamp spentAt = spentAtOf(id);
    assertThat(spentAt).isNotNull();
    assertThat(spentAt.toInstant()).isAfter(Instant.now().minusSeconds(60));
  }

  // A forged spend time on a row that is not spent is cleared.
  @Test
  void spentAtIsClearedOnRowsThatAreNotSpent() {
    UUID id = insertProof("UNSPENT");

    jdbc.update("UPDATE t_proof SET spent_at = now() WHERE id = ?", id);

    assertThat(spentAtOf(id)).isNull();
  }

  @Test
  void insertingASpentProofSetsSpentAt() {
    UUID id = insertProof("SPENT");

    assertThat(spentAtOf(id)).isNotNull();
  }

  @Test
  void markSpentSetsSpentAt() {
    UUID id = insertProof("UNSPENT");

    assertThat(proofRepository.markSpent(java.util.List.of("secret-" + id), mintId)).isEqualTo(1);

    assertThat(spentAtOf(id)).isNotNull();
  }

  @Test
  void commitSpentSetsSpentAt() {
    UUID id = insertProof("UNSPENT");
    String hold = "melt-" + id.toString().substring(0, 8);
    assertThat(proofRepository.markPending(java.util.List.of("secret-" + id), hold, mintId))
        .isEqualTo(1);

    assertThat(proofRepository.commitSpent(hold)).isEqualTo(1);

    assertThat(stateOf(id)).isEqualTo("SPENT");
    assertThat(spentAtOf(id)).isNotNull();
  }

  // Bookkeeping on a spent row (the archive flag) still works with the books columns frozen.
  @Test
  void archivingASpentProofKeepsItsSpendTime() {
    UUID id = insertProof("SPENT");
    Timestamp spentAt = spentAtOf(id);

    jdbc.update("UPDATE t_proof SET archived = true WHERE id = ?", id);

    assertThat(spentAtOf(id)).isEqualTo(spentAt);
  }

  private UUID insertProof(String state) {
    return insertProof(state, KEYSET);
  }

  private Timestamp spentAtOf(UUID id) {
    return jdbc.queryForObject("SELECT spent_at FROM t_proof WHERE id = ?", Timestamp.class, id);
  }

  private UUID insertProof(String state, String keysetId) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO t_proof (id, archived, created_at, updated_at, version, mint_id, amount, "
            + "secret, c, state, keyset_id) VALUES (?, false, ?, ?, 0, ?, 1, ?, ?, ?, ?)",
        id, now, now, mintId, "secret-" + id, "c-" + id, state, keysetId);
    return id;
  }

  private String stateOf(UUID id) {
    return jdbc.queryForObject("SELECT state FROM t_proof WHERE id = ?", String.class, id);
  }
}
