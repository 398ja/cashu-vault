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

    public DBKeyVault() {
        this(VaultClientFactory.getClient(KeyEntity.class));
    }

    public DBKeyVault(VaultClient<KeyEntity> client) {
        super(client);
    }

    public static Keys load(@Nonnull KeySetEntity keySetEntity) {
        return load(keySetEntity, null);
    }

    public static Keys load(@Nonnull KeySetEntity keySetEntity, Boolean archived) {
        Keys keys = new Keys();
        keySetEntity.getKeys()
                .stream()
                .filter(k -> archived == null || k.isArchived() == archived)
                .forEach(k -> {
                    keys.put(k.getAmount(), PrivateKey.derivePublicKey(PrivateKey.fromString(k.getPrivateKey())));
                });

        return keys;
    }

    @Override
    public KeyEntity store(KeyEntity keyEntity) throws CashuErrorException {
        keyEntity.setKeySet(getKeySet(keyEntity));
        return client.store(keyEntity);
    }

    private KeySetEntity getKeySet(KeyEntity keyEntity) {
        KeySetVaultClient keySetVaultClient = VaultClientFactory.keySetClient();
        return keySetVaultClient.getByKeySetId(keyEntity.getKeySet().getKeySetId());
    }

    @Override
    protected KeyEntity retrieveEntity(@Nonnull String id) throws CashuErrorException {
        KeyEntity keyEntity = client.retrieve(id);
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found");
        }
        return keyEntity;
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
        return keyEntity;
    }
}
