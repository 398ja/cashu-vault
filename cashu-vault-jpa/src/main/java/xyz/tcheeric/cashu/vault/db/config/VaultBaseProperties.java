package xyz.tcheeric.cashu.vault.db.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vault.base")
@Data
public class VaultBaseProperties {
    /** Base URL for the vault service. */
    private String url = "http://localhost:3333";
}
