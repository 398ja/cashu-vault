package xyz.tcheeric.cashu.vault.db.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Vault base settings.
 */
@Component
@ConfigurationProperties(prefix = "vault.base")
@Data
public class VaultBaseProperties {
    /**
     * Base URL where the Vault service is reachable.
     */
    private String url = "http://localhost:3333";
}
