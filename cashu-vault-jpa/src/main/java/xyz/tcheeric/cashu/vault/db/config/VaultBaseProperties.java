package xyz.tcheeric.cashu.vault.db.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the base URL of the vault service.
 */
@Component
@ConfigurationProperties(prefix = "vault.base")
@Data
public class VaultBaseProperties {
    /** Base URL for the vault service. */
    private String url = loadBaseUrl();

    private static String loadBaseUrl() {
        String env = System.getenv("VAULT_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty("vault.base.url");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String port = System.getenv("cashu_vault_port");
        if (port != null && !port.isBlank()) {
            return "http://localhost:" + port;
        }
        return "http://localhost:3333";
    }
}
