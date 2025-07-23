package xyz.tcheeric.cashu.vault.api.db.impl;

import jakarta.annotation.Nonnull;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

public class DBKeyVault extends DBVault<KeyEntity> {

    public DBKeyVault(KeyEntity entity) {
        super(entity, new CashuVaultApplication().vaultKeyClient());
    }

    public static Keys load(KeyEntity keyEntity, boolean archive) throws CashuErrorException {
        VaultClient<KeySetEntity> keySetVaultClient = new VaultClient<>(KeySetEntity.class);
        KeySetEntity keySetEntity = keySetVaultClient.retrieve(keyEntity.getKeySet().getId().toString());
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }

        Keys keys = new Keys();
        keySetEntity.getKeys().forEach(k -> {
            keys.put(k.getAmount(), PublicKey.fromString(k.getPrivateKey()));
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
