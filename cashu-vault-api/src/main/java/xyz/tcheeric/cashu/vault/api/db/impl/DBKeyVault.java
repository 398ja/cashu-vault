package xyz.tcheeric.cashu.vault.api.db.impl;

import jakarta.annotation.Nonnull;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.math.BigInteger;

public class DBKeyVault extends DBVault<KeyEntity> {

    public DBKeyVault(KeyEntity entity) {
        super(entity, new CashuVaultApplication().vaultKeyClient());
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
        KeySetVaultClient keySetVaultClient = new KeySetVaultClient();
        return keySetVaultClient.getByKeySetId(keyEntity.getKeySet().getKeySetId());
    }

    @Override
    protected KeyEntity retrieveEntity(@Nonnull String id) throws CashuErrorException {
        KeyVaultClient keyVaultClient = new KeyVaultClient();

        KeyEntity keyEntity = keyVaultClient.retrieve(id);
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found");
        }
        return keyEntity;
    }

    public static DBKeyVault retrieveKey(@Nonnull String id) throws CashuErrorException {
        DBKeyVault keyVault = new DBKeyVault(null);
        return new DBKeyVault(keyVault.retrieveEntity(id));
    }

    public static DBKeyVault retrieveKey(@Nonnull BigInteger amount, @Nonnull String keySetId) throws CashuErrorException {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
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
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        keyVaultClient.archive(this.getEntity().getId().toString());
    }

    @Override
    public void delete() throws CashuErrorException {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        keyVaultClient.delete(this.getEntity().getId().toString());
    }
}
