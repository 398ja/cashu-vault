package xyz.tcheeric.cashu.vault.db.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the configuration asks for the HashiCorp backend but it is not active.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>There were two switches with similar names and only one of them did anything (audit M-16).
 * {@code VAULT_BACKEND=HASHICORP} reads naturally as "store keys in HashiCorp" and is what
 * {@code docker-compose.prod.yml} set; the classes in {@code cashu-vault-hashi} are all
 * {@code @ConditionalOnProperty(vault.hashi.enabled=true)}, which that compose file never set.
 * A deployment could therefore declare the HashiCorp backend, boot cleanly, and store keys
 * through the JPA path instead, with nothing anywhere saying so.
 *
 * <p>Failing closed here is the point: the difference between the two backends is where the
 * mint's signing keys live, which is not something to discover from a later audit.
 */
@Slf4j
@Component
public class VaultBackendStartupValidator {

    @Value("${vault.backend:}")
    private String requestedBackend;

    @Value("${vault.hashi.enabled:false}")
    private boolean hashiEnabled;

    @PostConstruct
    void enforceBackendIsActive() {
        if (requestedBackend == null || requestedBackend.isBlank()) {
            if (hashiEnabled) {
                log.info("Vault backend: HashiCorp (vault.hashi.enabled=true).");
            } else {
                log.info("Vault backend: JPA. Keyset private keys are stored in the database.");
            }
            return;
        }
        boolean wantsHashi = "hashicorp".equalsIgnoreCase(requestedBackend.trim());
        if (wantsHashi && !hashiEnabled) {
            throw new IllegalStateException(
                    "vault.backend=" + requestedBackend + " requests the HashiCorp backend, but "
                            + "vault.hashi.enabled is false, so every class in cashu-vault-hashi "
                            + "is inactive and keys would be stored in the database instead. Set "
                            + "VAULT_HASHI_ENABLED=true (and the vault.hashi.* properties), or "
                            + "drop vault.backend to acknowledge the JPA backend.");
        }
        if (!wantsHashi && hashiEnabled) {
            log.warn("vault.backend={} but vault.hashi.enabled=true; the HashiCorp backend is "
                    + "active and takes precedence.", requestedBackend);
        }
        log.info("Vault backend: {} (vault.hashi.enabled={}).", requestedBackend, hashiEnabled);
    }
}
