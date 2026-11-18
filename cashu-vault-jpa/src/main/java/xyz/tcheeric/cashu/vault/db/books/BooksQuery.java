package xyz.tcheeric.cashu.vault.db.books;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate reads over {@code t_proof} for the books reconciler and the books metrics
 * (cashu-vault#156).
 *
 * <p>Plain SQL rather than JPQL: these are sums over potentially millions of rows and must stay
 * on the partial index {@code idx_proof_books_spent}, never hydrate an entity.
 */
@Repository
@RequiredArgsConstructor
public class BooksQuery {

    private static final String SPENT_BY_KEYSET = """
            SELECT p.keyset_id, k.unit, SUM(p.amount) AS amount, COUNT(*) AS cnt
              FROM t_proof p
              LEFT JOIN t_keyset k ON k.mint_id = p.mint_id AND k.key_set_id = p.keyset_id
             WHERE p.mint_id = :mintId AND p.state = 'SPENT'
               AND p.keyset_id IS NOT NULL AND p.spent_at <= :until
             GROUP BY p.keyset_id, k.unit
             ORDER BY p.keyset_id
            """;

    // Rows spent before V14 have neither a keyset nor a spend time, so they cannot be placed
    // relative to the watermark. They were all spent before the migration, hence before any
    // watermark a reconciler will ask about, and are counted in full.
    private static final String UNATTRIBUTED = """
            SELECT COALESCE(SUM(p.amount), 0) AS amount, COUNT(*) AS cnt
              FROM t_proof p
             WHERE p.mint_id = :mintId AND p.state = 'SPENT' AND p.keyset_id IS NULL
               AND (p.spent_at IS NULL OR p.spent_at <= :until)
            """;

    private static final String PENDING_BY_KEYSET = """
            SELECT p.keyset_id, p.hold_kind, SUM(p.amount) AS amount, COUNT(DISTINCT p.hold_id) AS holds
              FROM t_proof p
             WHERE p.mint_id = :mintId AND p.state = 'PENDING'
             GROUP BY p.keyset_id, p.hold_kind
             ORDER BY p.keyset_id, p.hold_kind
            """;

    private static final String TOTALS_BY_KEYSET = """
            SELECT p.mint_id, p.keyset_id, k.unit, p.state, SUM(p.amount) AS amount
              FROM t_proof p
              LEFT JOIN t_keyset k ON k.mint_id = p.mint_id AND k.key_set_id = p.keyset_id
             WHERE p.state IN ('SPENT', 'PENDING')
             GROUP BY p.mint_id, p.keyset_id, k.unit, p.state
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The books of one mint as of {@code until}.
     */
    public MintBooks booksOf(UUID mintId, Instant until) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mintId", mintId)
                .addValue("until", Timestamp.from(until));
        List<MintBooks.SpentByKeyset> spent = jdbc.query(SPENT_BY_KEYSET, params, (rs, i) ->
                new MintBooks.SpentByKeyset(rs.getString("keyset_id"), rs.getString("unit"),
                        rs.getLong("amount"), rs.getLong("cnt")));
        List<MintBooks.PendingByKeyset> pending = jdbc.query(PENDING_BY_KEYSET, params, (rs, i) ->
                new MintBooks.PendingByKeyset(rs.getString("keyset_id"), rs.getString("hold_kind"),
                        rs.getLong("amount"), rs.getLong("holds")));
        MintBooks.Unattributed unattributed = jdbc.queryForObject(UNATTRIBUTED, params, (rs, i) ->
                new MintBooks.Unattributed(rs.getLong("amount"), rs.getLong("cnt")));
        return new MintBooks(until, spent, pending, unattributed);
    }

    /** One row of the all-mints totals behind the books metrics. */
    public record KeysetTotal(UUID mintId, String keysetId, String unit, String state, long amount) {
    }

    /**
     * Spent and pending totals of every mint and keyset, now. Backs the metrics.
     */
    public List<KeysetTotal> currentTotals() {
        return jdbc.query(TOTALS_BY_KEYSET, (rs, i) -> new KeysetTotal(
                rs.getObject("mint_id", UUID.class), rs.getString("keyset_id"), rs.getString("unit"),
                rs.getString("state"), rs.getLong("amount")));
    }
}
