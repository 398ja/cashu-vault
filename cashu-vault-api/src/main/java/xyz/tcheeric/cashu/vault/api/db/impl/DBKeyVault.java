package xyz.tcheeric.cashu.vault.api.db.impl;

import jakarta.annotation.Nonnull;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.math.BigInteger;

public class DBKeyVault extends DBVault<KeyEntity> {

    public DBKeyVault(KeyEntity entity) {
        this(entity, VaultClientFactory.getClient(KeyEntity.class));
    }

    public DBKeyVault(KeyEntity entity, VaultClient<KeyEntity> client) {
        super(entity, client);
    }

    public static Keys load(@Nonnull KeySetEntity keySetEntity) {
        return load(keySetEntity, null);
    }

    public static Keys load(@Nonnull KeySetEntity keySetEntity, Boolean archived) {
        Keys keys = new Keys();
        keySetEntity.getKeys()
                .stream()
                .filter(k -> archived != null ? k.isArchived() == archived : true)
                .forEach(k -> {
                    keys.put(k.getAmount(), PrivateKey.derivePublicKey(PrivateKey.fromString(k.getPrivateKey())));
                });

        return keys;
    }

    @Override
    public void store() {
        KeyEntity keyEntity = getEntity();
        VaultClient<KeyEntity> client = getClient();
        keyEntity.setKeySet(getKeySet(keyEntity));
        client.store(keyEntity);
    }

    private KeySetEntity getKeySet(KeyEntity keyEntity) {
        KeySetVaultClient keySetVaultClient = VaultClientFactory.keySetClient();
        return keySetVaultClient.getByKeySetId(keyEntity.getKeySet().getKeySetId());
    }

    @Override
    protected KeyEntity retrieveEntity(@Nonnull String id) throws CashuErrorException {
        VaultClient<KeyEntity> keyVaultClient = getClient();

        KeyEntity keyEntity = keyVaultClient.retrieve(id);
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found");
        }
        return keyEntity;
    }

    public static DBKeyVault retrieveKey(@Nonnull String id) throws CashuErrorException {
        VaultClient<KeyEntity> client = VaultClientFactory.getClient(KeyEntity.class);
        return retrieveKey(id, client);
    }

    public static DBKeyVault retrieveKey(@Nonnull String id, VaultClient<KeyEntity> client) throws CashuErrorException {
        DBKeyVault keyVault = new DBKeyVault(null, client);
        return new DBKeyVault(keyVault.retrieveEntity(id), client);
    }

    public static DBKeyVault retrieveKey(@Nonnull BigInteger amount, @Nonnull String keySetId) throws CashuErrorException {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        KeyEntity keyEntity = keyVaultClient.getKeysByKeySetId(keySetId).stream()
                .filter(k -> k.getAmount().equals(amount))
                .findFirst()
                .orElse(null);
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found for amount: " + amount + " and keySetId: " + keySetId);
        }
        return new DBKeyVault(keyEntity);
    }

    @Override
    public void archive() throws CashuErrorException {
        VaultClient<KeyEntity> keyVaultClient = getClient();
        keyVaultClient.archive(this.getEntity().getId().toString());
    }

    @Override
    public void delete() throws CashuErrorException {
        VaultClient<KeyEntity> keyVaultClient = getClient();
        keyVaultClient.delete(this.getEntity().getId().toString());
    }
}
