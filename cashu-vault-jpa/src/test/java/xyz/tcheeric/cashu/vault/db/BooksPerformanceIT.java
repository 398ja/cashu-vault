package xyz.tcheeric.cashu.vault.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import xyz.tcheeric.cashu.vault.db.books.BooksQuery;
import xyz.tcheeric.cashu.vault.db.books.MintBooks;

/**
 * cashu-vault#156: the books of a mint with 1M proofs answer in under 2s on PostgreSQL.
 *
 * <p>Seeds 1M rows with {@code generate_series} (900k SPENT across four keysets, the rest UNSPENT
 * or PENDING), plus a second mint so the index has to discriminate, then times the books query the
 * endpoint runs.
 */
@Testcontainers
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BooksPerformanceIT {

  private static final int ROWS = 1_000_000;

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

  @Autowired private DataSource dataSource;
  @Autowired private BooksQuery booksQuery;

  private final UUID mintId = UUID.randomUUID();
  private final UUID otherMintId = UUID.randomUUID();

  @BeforeAll
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Timestamp now = Timestamp.from(Instant.now());
    for (UUID mint : new UUID[] {mintId, otherMintId}) {
      jdbc.update(
          "INSERT INTO t_mint (id, archived, created_at, updated_at, version) VALUES (?, false, ?,"
              + " ?, 0)",
          mint,
          now,
          now);
      for (int k = 0; k < 4; k++) {
        jdbc.update(
            "INSERT INTO t_keyset (id, archived, created_at, updated_at, version, key_set_id, unit,"
                + " mint_id, input_fee_ppk) VALUES (?, ?, ?, ?, 0, ?, 'sat', ?, 0)",
            UUID.randomUUID(),
            k != 0,
            now,
            now,
            keyset(mint, k),
            mint);
      }
    }
    seedProofs(jdbc, mintId, ROWS);
    seedProofs(jdbc, otherMintId, ROWS / 4);
    jdbc.execute("ANALYZE t_proof");
  }

  private static void seedProofs(JdbcTemplate jdbc, UUID mint, int rows) {
    // The trigger stamps spent_at = now() on every SPENT insert, which is what production does.
    jdbc.update(
        "INSERT INTO t_proof (id, archived, created_at, updated_at, version, mint_id, amount,"
            + " secret, c, state, keyset_id, hold_id, hold_kind) SELECT gen_random_uuid(), false,"
            + " now(), now(), 0, ?, (1 << (g % 10)),   ? || '-s-' || g, ? || '-c-' || g,   CASE"
            + " WHEN g % 10 < 9 THEN 'SPENT' WHEN g % 20 = 9 THEN 'PENDING' ELSE 'UNSPENT' END,   ?"
            + " || (g % 4),   CASE WHEN g % 20 = 9 THEN 'swap-' || (g / 20) END,   CASE WHEN g % 20"
            + " = 9 THEN 'SWAP' END FROM generate_series(1, ?) AS g",
        mint, mint.toString(), mint.toString(), keysetPrefix(mint), rows);
  }

  private static String keysetPrefix(UUID mint) {
    return "00" + mint.toString().replace("-", "").substring(0, 13);
  }

  private static String keyset(UUID mint, int k) {
    return keysetPrefix(mint) + k;
  }

  @Test
  void booksOverAMillionProofsIsUnderTwoSeconds() {
    Instant until = Instant.now();
    booksQuery.booksOf(mintId, until); // warm the buffer cache once, as a running service has

    long start = System.nanoTime();
    MintBooks books = booksQuery.booksOf(mintId, until);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    org.slf4j.LoggerFactory.getLogger(BooksPerformanceIT.class)
        .info("books over {} proofs took {} ms", ROWS, elapsed.toMillis());
    assertThat(books.spent()).hasSize(4);
    assertThat(books.spent().stream().mapToLong(MintBooks.SpentByKeyset::count).sum())
        .isEqualTo(ROWS / 10 * 9);
    assertThat(books.pending().get(0).holds()).isEqualTo(ROWS / 20);
    assertThat(elapsed)
        .as("books over %d proofs took %s", ROWS, elapsed)
        .isLessThan(Duration.ofSeconds(2));
  }
}
