package xyz.tcheeric.cashu.vault.db.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration enabling JPA auditing for the vault module.
 */
@Configuration
@EnableJpaAuditing
public class AuditConfig {
    // no additional code needed here
}
