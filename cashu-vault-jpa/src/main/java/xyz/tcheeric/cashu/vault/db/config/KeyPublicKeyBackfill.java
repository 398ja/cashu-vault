package xyz.tcheeric.cashu.vault.db.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.api.KeyPublicKeys;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeyRepository;
import xyz.tcheeric.cashu.vault.hashi.KeyVaultPaths;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.util.List;
import java.util.Map;

/**
 * Fills in the public key of keys written before the column existed, once, at startup.
 *
 * <h2>Why a backfill and not a lazy derive</h2>
 *
 * <p>{@code DBKeySetVault.load} answers a key with no stored public key by reading its private
 * key and deriving, which is exactly the per-key round trip issue #146 exists to remove. Without
 * this, every key provisioned before the V12 migration would keep paying it forever, and the
 * optimisation would appear to change nothing on an existing deployment: precisely the outcome
 * the two previous attempts in cashu-mint had.
 *
 * <p>It runs on {@link ApplicationReadyEvent} rather than {@code @PostConstruct} so it cannot
 * delay or fail the startup it follows. A key that cannot be backfilled is left alone and the
 * load path still answers correctly for it, so the worst case is the cost this repo had before.
 *
 * <h2>What this does not do</h2>
 *
 * <p>No private key is stored, logged or cached. Each one is read, derived from, and dropped
 * with the loop iteration. The derived public key is what the mint already publishes under
 * NUT-01.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyPublicKeyBackfill {

    private final KeyRepository keyRepository;

    /**
     * Absent unless {@code vault.hashi.enabled=true}, because every class in cashu-vault-hashi
     * is conditional on it. There is nothing to backfill from without it: the private keys a
     * public key derives from are in HashiCorp Vault, not in this database.
     */
    private final ObjectProvider<HashiVaultClient> hashiClientProvider;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingPublicKeys() {
        final HashiVaultClient hashiClient = hashiClientProvider.getIfAvailable();
        if (hashiClient == null) {
            return;
        }
        final List<KeyEntity> pending = keyRepository.findByPublicKeyIsNull();
        if (pending.isEmpty()) {
            return;
        }
        int backfilled = 0;
        for (final KeyEntity key : pending) {
            if (stampPublicKey(key, hashiClient)) {
                backfilled++;
            }
        }
        keyRepository.saveAll(pending);
        log.info("key_public_key_backfill pending={} backfilled={} (#146)", pending.size(),
                backfilled);
    }

    private boolean stampPublicKey(final KeyEntity key, final HashiVaultClient hashiClient) {
        try {
            final String privateKeyHex = readPrivateKey(key, hashiClient);
            if (privateKeyHex == null) {
                return false;
            }
            key.setPublicKey(KeyPublicKeys.deriveFrom(privateKeyHex));
            return true;
        } catch (final RuntimeException e) {
            // One unreadable key must not abandon the rest, and it is not fatal: the load path
            // still derives for whatever is left unstamped. Logged by id only, never by value.
            log.warn("Could not backfill the public key of key {}", key.getId(), e);
            return false;
        }
    }

    private String readPrivateKey(final KeyEntity key, final HashiVaultClient hashiClient) {
        if (key.getVaultPath() == null || key.getVaultPath().isBlank()) {
            return null;
        }
        // The same ownership check the read paths apply (audit M-13): vault_path is writable
        // through POST /vault/key, so a row pointed at another key's secret must not be read
        // here either, however harmless deriving a public key sounds.
        if (!KeyVaultPaths.isPathForEntity(key.getVaultPath(), key)) {
            log.error("Key entity {} has a vault path that is not its own: refusing to read",
                    key.getId());
            return null;
        }
        final Map<String, Object> secret = hashiClient.getSecret(key.getVaultPath());
        return secret != null ? (String) secret.get("private_key") : null;
    }
}
