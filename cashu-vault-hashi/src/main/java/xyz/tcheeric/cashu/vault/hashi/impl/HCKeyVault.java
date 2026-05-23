package xyz.tcheeric.cashu.vault.hashi.impl;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
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
public final class HCKeyVault extends DBVault<KeyEntity> {

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
        return String.format("keys/%s/%s/%s",
                key.getKeySet().getMint().getId(),
                key.getKeySet().getKeySetId(),
                key.getAmount());
    }
}
