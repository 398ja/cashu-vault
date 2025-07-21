package xyz.tcheeric.cashu.vault.api.db.impl;

import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.config.KeyConfiguration;
import xyz.tcheeric.cashu.vault.api.config.KeysetConfiguration;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

public class DBKeyVault extends DBVault<KeyConfiguration, KeyEntity> {

    public DBKeyVault(KeyConfiguration configuration) {
        super(configuration, new CashuVaultApplication().vaultKeyClient());
    }

    public static Keys load(KeyConfiguration keyConfiguration, boolean archive) throws CashuErrorException {
        VaultClient<KeySetEntity> keySetVaultClient = new VaultClient<>(KeySetEntity.class);
        KeysetConfiguration keysetConfiguration = keyConfiguration.getKeyset();
        KeySetEntity keySetEntity = keySetVaultClient.retrieve(keysetConfiguration.getId());
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }

        Keys keys = new Keys();
        keySetEntity.getKeys().forEach(keyEntity -> {
            keys.put(keyEntity.getAmount(), PublicKey.fromString(keyEntity.getPrivateKey()));
        });

        return keys;
    }

    @Override
    public void store() {
        KeyConfiguration keyConfiguration = getConfiguration();
        VaultClient<KeyEntity> client = getClient();

        KeyEntity keyEntity = new KeyEntity();
        keyEntity.setPrivateKey(keyConfiguration.getPrivateKey());
        keyEntity.setKeySet(getKeySet(keyConfiguration));
        keyEntity.setAmount(keyConfiguration.getAmount());

        client.store(keyEntity);
    }

    private KeySetEntity getKeySet(KeyConfiguration keyConfiguration) {
        KeySetVaultClient keySetVaultClient = new KeySetVaultClient();
        return keySetVaultClient.getByKeySetId(keyConfiguration.getKeyset().getId());
    }

    @Override
    public String retrieve(boolean archived) throws CashuErrorException {
        KeyEntity keyEntity = retrieveEntity();
        return keyEntity.isArchived() != archived ? null : keyEntity.getPrivateKey();
    }

    @Override
    protected KeyEntity retrieveEntity() throws CashuErrorException {
        KeyConfiguration keyConfiguration = getConfiguration();
        KeyVaultClient keyVaultClient = new KeyVaultClient();

        KeyEntity keyEntity = keyVaultClient.getByPrivateKey(keyConfiguration.getPrivateKey());
        if (keyEntity == null) {
            throw new CashuErrorException("Key not found");
        }
        return keyEntity;
    }

    @Override
    public void archive() throws CashuErrorException {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        KeyEntity keyEntity = retrieveEntity();
        keyVaultClient.archive(keyEntity.getId().toString());
    }

    @Override
    public void delete() throws CashuErrorException {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        KeyEntity keyEntity = retrieveEntity();
        keyVaultClient.delete(keyEntity.getId().toString());
    }
}
