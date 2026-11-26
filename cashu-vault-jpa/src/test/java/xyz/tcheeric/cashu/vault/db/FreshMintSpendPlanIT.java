package xyz.tcheeric.cashu.vault.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

/**
 * A spend against a mint the planner's statistics have not seen yet must still use {@code
 * uk_proof_mint_secret}, not filter every proof of the mint.
 *
 * <p>Runs against real PostgreSQL: the plan choice has no H2 counterpart. The plan is observed
 * through {@code pg_stat_user_indexes}, so the test exercises the exact SQL the repository emits.
 */
@Testcontainers
@SpringBootTest
class FreshMintSpendPlanIT {

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
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  private static final int KNOWN_MINTS = 4;
  private static final int PROOFS_PER_MINT = 14_400;
  private static final List<String> FRESH_SECRETS = List.of("f-1", "f-2", "f-3");
  private static final String MINT_ONLY_INDEX_TUPLES_READ =
      "SELECT coalesce(sum(idx_tup_read), 0) FROM pg_stat_user_indexes "
          + "WHERE indexrelname IN ('idx_proof_mint_id', 'idx_proof_mint_counter')";
  private static final String UNIQUE_KEY_SCANS =
      "SELECT idx_scan FROM pg_stat_user_indexes WHERE indexrelname = 'uk_proof_mint_secret'";
  private static final Duration STATS_FLUSH_TIMEOUT = Duration.ofSeconds(15);

  @Autowired private DataSource dataSource;
  @Autowired private ProofRepository proofRepository;

  private JdbcTemplate jdbc;
  private UUID freshMint;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    for (int m = 0; m < KNOWN_MINTS; m++) {
      seedMint(UUID.randomUUID(), "SPENT");
    }
    jdbc.execute("ANALYZE t_proof");
    freshMint = UUID.randomUUID();
    seedMint(freshMint, "UNSPENT");
  }

  // Marking proofs of a mint the statistics have never seen spent, then counting them, must probe
  // uk_proof_mint_secret rather than walk a mint-only index across all 14,400 proofs of the mint.
  @Test
  void freshMintSpendProbesTheMintSecretIndex() {
    long tuplesBefore = cumulativeIndexStat(MINT_ONLY_INDEX_TUPLES_READ);
    long uniqueScansBefore = cumulativeIndexStat(UNIQUE_KEY_SCANS);

    assertThat(proofRepository.markSpent(FRESH_SECRETS, freshMint)).isEqualTo(FRESH_SECRETS.size());
    assertThat(proofRepository.countSpent(FRESH_SECRETS, freshMint))
        .isEqualTo(FRESH_SECRETS.size());

    awaitStatsFlush(uniqueScansBefore + 2);
    assertThat(cumulativeIndexStat(MINT_ONLY_INDEX_TUPLES_READ)).isEqualTo(tuplesBefore);
  }

  // Secondary measurement for the PR: best-of-20 latency of the spend count on a fresh mint.
  // Prints rather than asserts on timings to stay stable on CI.
  @Test
  void reportsFreshMintSpendLatency() {
    double best = Double.MAX_VALUE;
    for (int i = 0; i < 20; i++) {
      long start = System.nanoTime();
      proofRepository.countSpent(FRESH_SECRETS, freshMint);
      best = Math.min(best, (System.nanoTime() - start) / 1e6);
    }
    System.out.printf("fresh_mint_count_ms: %.3f%n", best);
    assertThat(best).isPositive();
  }

  // Index statistics are flushed by the backend that did the work shortly after it goes idle, so
  // wait until the unique-key scans show up before reading the mint-only counters.
  private void awaitStatsFlush(long expectedUniqueScans) {
    long deadline = System.nanoTime() + STATS_FLUSH_TIMEOUT.toNanos();
    while (cumulativeIndexStat(UNIQUE_KEY_SCANS) < expectedUniqueScans) {
      assertThat(System.nanoTime()).as("index statistics flushed").isLessThan(deadline);
      sleepBriefly();
    }
  }

  private long cumulativeIndexStat(String query) {
    return new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        .execute(
            status -> {
              jdbc.execute("SELECT pg_stat_clear_snapshot()");
              return jdbc.queryForObject(query, Long.class);
            });
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for index statistics", e);
    }
  }

  private void seedMint(UUID mint, String state) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO t_mint (id, archived, created_at, updated_at, version) VALUES (?, false, ?, ?,"
            + " 0)",
        mint,
        now,
        now);
    String prefix = mint.equals(freshMint) ? "f-" : mint + "-";
    jdbc.update(
        "INSERT INTO t_proof (id, archived, created_at, updated_at, version, mint_id, amount,"
            + " secret, c, state) SELECT gen_random_uuid(), false, now(), now(), 0, ?, 1, ? || g, ?"
            + " || 'c-' || g, ? FROM generate_series(0, ?) g",
        mint,
        prefix,
        prefix,
        state,
        PROOFS_PER_MINT - 1);
  }
}
