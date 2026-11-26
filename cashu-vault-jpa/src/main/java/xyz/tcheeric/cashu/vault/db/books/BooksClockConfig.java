package xyz.tcheeric.cashu.vault.db.books;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The clock the books endpoint checks {@code until} against; replaceable in tests. */
@Configuration
class BooksClockConfig {

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }
}
