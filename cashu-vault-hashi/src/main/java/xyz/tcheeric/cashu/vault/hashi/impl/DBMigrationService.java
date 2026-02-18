package xyz.tcheeric.cashu.vault.hashi.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for migrating private keys from the database to HashiCorp Vault (Phase A).
 * <p>
 * For each key that has a private_key value and no vault_path, this service will:
 * 1. Write the private key to HashiCorp Vault at the appropriate path
 * 2. Update the KeyEntity with the vault_path reference
 * 3. Optionally scrub the private_key column value from the database
 */
@Service
@ConditionalOnProperty(name = "vault.backend", havingValue = "hashicorp")
@RequiredArgsConstructor
@Slf4j
public class DBMigrationService {

    private final HashiVaultClient hashiClient;

    /**
     * Migrates all keys for a given keyset from DB to HashiCorp Vault.
     *
     * @param keySetId the keyset identifier
     * @return number of keys migrated
     */
    public int migrateKeySet(String keySetId) {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        Set<KeyEntity> keys = keyVaultClient.getKeysByKeySetId(keySetId);
        AtomicInteger migrated = new AtomicInteger(0);

        keys.forEach(key -> {
            if (key.getPrivateKey() != null && key.getVaultPath() == null) {
                migrateKey(key);
                migrated.incrementAndGet();
            }
        });

        log.info("Migrated {} keys from keyset {} to HashiCorp Vault", migrated.get(), keySetId);
        return migrated.get();
    }

    /**
     * Migrates all keys for all keysets of a given mint from DB to HashiCorp Vault.
     *
     * @param mintId the mint identifier
     * @return number of keys migrated
     */
    public int migrateMint(String mintId) {
        KeySetVaultClient keySetClient = VaultClientFactory.keySetClient();
        Set<KeySetEntity> keySets = keySetClient.getByMintId(mintId);
        int total = 0;

        for (KeySetEntity keySet : keySets) {
            total += migrateKeySet(keySet.getKeySetId());
        }

        log.info("Migrated {} keys total from mint {} to HashiCorp Vault", total, mintId);
        return total;
    }

    /**
     * Verifies that a key's secret in HashiCorp Vault matches the DB value.
     *
     * @param key the key entity with both private_key and vault_path populated
     * @return true if the values match
     */
    public boolean verifyKey(KeyEntity key) {
        if (key.getVaultPath() == null || key.getPrivateKey() == null) {
            return false;
        }
        Map<String, Object> data = hashiClient.getSecret(key.getVaultPath());
        if (data == null) {
            return false;
        }
        return key.getPrivateKey().equals(data.get("private_key"));
    }

    /**
     * Scrubs the private_key column from the database for keys that have been migrated
     * to HashiCorp Vault (i.e., have a vault_path set).
     *
     * @param keySetId the keyset identifier
     * @return number of keys scrubbed
     */
    public int scrubAndRedact(String keySetId) {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        VaultClient<KeyEntity> genericClient = VaultClientFactory.getClient(KeyEntity.class);
        Set<KeyEntity> keys = keyVaultClient.getKeysByKeySetId(keySetId);
        AtomicInteger scrubbed = new AtomicInteger(0);

        keys.forEach(key -> {
            if (key.getVaultPath() != null && key.getPrivateKey() != null) {
                if (verifyKey(key)) {
                    key.setPrivateKey(null);
                    genericClient.store(key);
                    scrubbed.incrementAndGet();
                    log.info("Scrubbed private_key from DB for key: {}", key.getId());
                } else {
                    log.warn("Verification failed for key: {} — skipping scrub", key.getId());
                }
            }
        });

        log.info("Scrubbed {} keys from keyset {}", scrubbed.get(), keySetId);
        return scrubbed.get();
    }

    private void migrateKey(KeyEntity key) {
        String path = String.format("keys/%s/%s/%s",
                key.getKeySet().getMint().getId(),
                key.getKeySet().getKeySetId(),
                key.getAmount());

        Map<String, Object> data = Map.of(
                "private_key", key.getPrivateKey(),
                "created_at", key.getCreatedAt().toString()
        );

        String vaultPath = hashiClient.storeSecret(path, data);
        log.info("Migrated key {} to HashiCorp Vault at: {}", key.getId(), vaultPath);

        // Update DB with vault reference (keep private_key for verification)
        key.setVaultPath(vaultPath);
        VaultClient<KeyEntity> genericClient = VaultClientFactory.getClient(KeyEntity.class);
        genericClient.store(key);
    }
}
