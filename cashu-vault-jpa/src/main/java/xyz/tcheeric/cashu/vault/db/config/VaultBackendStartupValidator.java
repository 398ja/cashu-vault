package xyz.tcheeric.cashu.vault.db.config;

import jakarta.annotation.PostConstruct;

import java.util.Locale;
import java.util.Set;
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

    /**
     * Spellings accepted as "use HashiCorp Vault".
     *
     * <p>Deliberately generous, and paired with refusing anything outside these sets. The
     * alternative, matching one exact string and treating everything else as JPA, is what let
     * VAULT_BACKEND=HASHICORP-with-a-typo start the service on database storage.
     */
    private static final Set<String> HASHICORP_ALIASES =
            Set.of("hashicorp", "hashi", "vault", "hashicorp-vault", "hashivault");

    /** Spellings accepted as "store in the database". */
    private static final Set<String> JPA_ALIASES =
            Set.of("jpa", "db", "database", "postgres", "postgresql");

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
        final String backend = requestedBackend.trim();
        final boolean wantsHashi = HASHICORP_ALIASES.contains(backend.toLowerCase(Locale.ROOT));
        final boolean wantsJpa = JPA_ALIASES.contains(backend.toLowerCase(Locale.ROOT));

        // An unrecognised value must not be read as "not hashicorp". Matching only the exact
        // string "hashicorp" meant VAULT_BACKEND=HASHI, or VAULT, or a typo, fell through to the
        // warn-only branch and the service started on JPA while the operator believed otherwise.
        // That is precisely the M-16 incident this validator exists to prevent, reachable through
        // any spelling but one. Refusing an unknown value is the only safe reading, because the
        // deployment plainly intended something.
        if (!wantsHashi && !wantsJpa) {
            throw new IllegalStateException(
                    "vault.backend=" + requestedBackend + " is not a recognised backend. Use one "
                            + "of " + HASHICORP_ALIASES + " for HashiCorp Vault, or "
                            + JPA_ALIASES + " for database storage. An unrecognised value is "
                            + "refused rather than assumed, because assuming meant silently "
                            + "storing keyset private keys in the database.");
        }
        if (wantsHashi && !hashiEnabled) {
            throw new IllegalStateException(
                    "vault.backend=" + requestedBackend + " requests the HashiCorp backend, but "
                            + "vault.hashi.enabled is false, so every class in cashu-vault-hashi "
                            + "is inactive and keys would be stored in the database instead. Set "
                            + "VAULT_HASHI_ENABLED=true (and the vault.hashi.* properties), or "
                            + "drop vault.backend to acknowledge the JPA backend.");
        }
        if (wantsJpa && hashiEnabled) {
            log.warn("vault.backend={} but vault.hashi.enabled=true; the HashiCorp backend is "
                    + "active and takes precedence.", requestedBackend);
        }
        log.info("Vault backend: {} (vault.hashi.enabled={}).", requestedBackend, hashiEnabled);
    }
}
