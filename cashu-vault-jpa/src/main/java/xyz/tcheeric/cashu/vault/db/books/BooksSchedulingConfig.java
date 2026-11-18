package xyz.tcheeric.cashu.vault.db.books;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the 60s poll behind {@link BooksMetrics}. {@code vault.books.metrics.enabled=false}
 * switches the poll off (the test suite does, and calls {@link BooksMetrics#refresh} directly).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vault.books.metrics.enabled", havingValue = "true", matchIfMissing = true)
class BooksSchedulingConfig {
}
