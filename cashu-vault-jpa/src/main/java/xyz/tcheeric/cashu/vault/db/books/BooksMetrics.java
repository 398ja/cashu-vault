package xyz.tcheeric.cashu.vault.db.books;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spent and pending value per mint and keyset as Prometheus metrics (cashu-vault#156).
 *
 * <ul>
 *   <li>{@code cashu_vault_spent_amount_total{mint,keyset,unit}}: a function counter over the spent
 *       total. Spent rows are final (V13/V14 trigger), so it only goes up; a decrease is an alert
 *       on its own.
 *   <li>{@code cashu_vault_pending_amount{mint,keyset,unit}}: a gauge of the value held by melts
 *       and swaps now.
 * </ul>
 *
 * <p>The values come from one aggregate query polled every 60s (as in cashu-mint ADR 0002), not
 * from the scrape, so a scrape never costs a table scan. Spent proofs without a keyset are reported
 * under {@code keyset="unattributed"}, and a keyset the vault does not know under {@code
 * unit="unknown"}.
 */
@Slf4j
@Component
public class BooksMetrics {

  static final String SPENT = "cashu_vault_spent_amount";
  static final String PENDING = "cashu_vault_pending_amount";
  static final String UNATTRIBUTED = "unattributed";
  static final String UNKNOWN_UNIT = "unknown";

  private final BooksQuery booksQuery;
  private final MeterRegistry registry;
  private final Map<Tags, AtomicLong> spent = new ConcurrentHashMap<>();
  private final Map<Tags, AtomicLong> pending = new ConcurrentHashMap<>();

  public BooksMetrics(BooksQuery booksQuery, MeterRegistry registry) {
    this.booksQuery = booksQuery;
    this.registry = registry;
  }

  /**
   * Re-reads the totals. A failed read keeps the previous values rather than zeroing them, so a
   * database blip does not look like value disappearing.
   */
  @Scheduled(
      initialDelayString = "${vault.books.metrics.initial-delay:5000}",
      fixedDelayString = "${vault.books.metrics.interval:60000}")
  public void refresh() {
    try {
      Set<Tags> pendingSeen = new HashSet<>();
      for (BooksQuery.KeysetTotal total : booksQuery.currentTotals()) {
        Tags tags =
            Tags.of(
                "mint",
                total.mintId().toString(),
                "keyset",
                total.keysetId() == null ? UNATTRIBUTED : total.keysetId(),
                "unit",
                total.unit() == null ? UNKNOWN_UNIT : total.unit());
        if ("SPENT".equals(total.state())) {
          spentCell(tags).set(total.amount());
        } else {
          pendingCell(tags).set(total.amount());
          pendingSeen.add(tags);
        }
      }
      // A hold that resolved leaves no PENDING row behind: its gauge must drop to 0.
      pending.forEach((tags, cell) -> {
        if (!pendingSeen.contains(tags)) {
          cell.set(0);
        }
      });
    } catch (RuntimeException e) {
      log.warn("books metrics refresh failed, keeping previous values: {}", e.toString());
    }
  }

  private AtomicLong spentCell(Tags tags) {
    return spent.computeIfAbsent(
        tags,
        t -> {
          AtomicLong cell = new AtomicLong();
          FunctionCounter.builder(SPENT, cell, AtomicLong::doubleValue)
              .description("Value of SPENT proofs per mint and keyset")
              .tags(t)
              .register(registry);
          return cell;
        });
  }

  private AtomicLong pendingCell(Tags tags) {
    return pending.computeIfAbsent(
        tags,
        t -> {
          AtomicLong cell = new AtomicLong();
          Gauge.builder(PENDING, cell, AtomicLong::doubleValue)
              .description("Value of PENDING proofs (held by a melt or swap) per mint and keyset")
              .tags(t)
              .register(registry);
          return cell;
        });
  }
}
