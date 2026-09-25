package xyz.tcheeric.cashu.vault.hashi.impl;

import xyz.tcheeric.cashu.vault.hashi.KeyVaultPaths;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.KeyPublicKeys;
import xyz.tcheeric.cashu.vault.api.KeyVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.math.BigInteger;
import java.util.Map;

@Slf4j
public final class HCKeyVault extends DBVault<KeyEntity> implements KeyVault {

    private final HashiVaultClient hashiClient;

    public HCKeyVault(HashiVaultClient hashiClient) {
        super(VaultClientFactory.getClient(KeyEntity.class));
        this.hashiClient = hashiClient;
    }

    public HCKeyVault(VaultClient<KeyEntity> client, HashiVaultClient hashiClient) {
        super(client);
        this.hashiClient = hashiClient;
    }

    @Override
    public KeyEntity store(KeyEntity keyEntity) throws CashuErrorException {
        if (keyEntity.getPrivateKey() == null) {
            throw new CashuErrorException("Private key must not be null");
        }
        keyEntity.setKeySet(getKeySet(keyEntity));

        // Derived and recorded here because this is the last point at which the private key is
        // in hand. Once it is in HashiCorp Vault, recovering the public key costs a secret read
        // per key, which is the round trip issue #146 removed.
        KeyPublicKeys.stampOn(keyEntity);

        // Store private key in HashiCorp Vault
        String path = buildPath(keyEntity);
        Map<String, Object> data = Map.of(
                "private_key", keyEntity.getPrivateKey(),
                "created_at", keyEntity.getCreatedAt().toString()
        );
        String vaultPath = hashiClient.storeSecret(path, data);
        log.info("Stored private key in HashiCorp Vault at: {}", vaultPath);

        // Persist metadata and vault reference to DB (without the secret)
        keyEntity.setVaultPath(vaultPath);
        keyEntity.setPrivateKey(null);
        return client.store(keyEntity);
    }

    @Override
    protected KeyEntity retrieveEntity(@Nonnull String id) throws CashuErrorException {
        KeyEntity entity = client.retrieve(id);
        if (entity == null) {
            throw new CashuErrorException("Key not found");
        }
        enrichWithVaultSecret(entity);
        return entity;
    }

    public KeyEntity retrieveByAmount(@Nonnull BigInteger amount, @Nonnull String keySetId) throws CashuErrorException {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        KeyEntity keyEntity = keyVaultClient.getKeysByKeySetId(keySetId).stream()
                .filter(k -> k.getAmount().equals(amount))
                .findFirst()
                .orElse(null);
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found for amount: " + amount + " and keySetId: " + keySetId);
        }
        enrichWithVaultSecret(keyEntity);
        return keyEntity;
    }

    private void enrichWithVaultSecret(KeyEntity entity) {
        if (entity.getVaultPath() == null) {
            log.warn("Key entity {} has no vault path, cannot enrich with secret", entity.getId());
            return;
        }
        // The stored path is used as a KV read path, and the row it comes from is writable
        // through POST /vault/key, so before authentication existed a caller could point a key
        // row at any path in the mount and read it back through this method (audit M-13).
        // Authentication closed the front door; this closes the path itself, because a read path
        // assembled from stored data should not be able to escape its prefix regardless of who
        // wrote it.
        if (!KeyVaultPaths.isPathForEntity(entity.getVaultPath(), entity)) {
            log.error("Key entity {} has a vault path that is not its own: refusing to read",
                    entity.getId());
            throw new IllegalStateException(
                    "Refusing to read a vault path that does not belong to this key");
        }
        Map<String, Object> data = hashiClient.getSecret(entity.getVaultPath());
        if (data != null) {
            entity.setPrivateKey((String) data.get("private_key"));
        }
    }

    private KeySetEntity getKeySet(KeyEntity keyEntity) {
        KeySetVaultClient keySetVaultClient = VaultClientFactory.keySetClient();
        return keySetVaultClient.getByKeySetId(keyEntity.getKeySet().getKeySetId());
    }

    private String buildPath(KeyEntity key) {
        return KeyVaultPaths.buildPath(key);
    }
}
