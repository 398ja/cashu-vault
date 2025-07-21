package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.config.KeyConfiguration;
import xyz.tcheeric.cashu.vault.api.config.KeysetConfiguration;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.Set;

public class DBKeySetVault extends DBVault<KeysetConfiguration, KeySetEntity> {

    public DBKeySetVault(KeysetConfiguration configuration) {
        super(configuration, new CashuVaultApplication().vaultKeySetClient());
    }

    @Override
    public void store() {
        KeysetConfiguration keysetConfiguration = getConfiguration();
        VaultClient<KeySetEntity> client = getClient();

        KeySetEntity keySetEntity = new KeySetEntity();
        keySetEntity.setKeySetId(keysetConfiguration.getId());
        keySetEntity.setUnit(keysetConfiguration.getUnit());
        keySetEntity.setMint(getMint(keysetConfiguration));

        client.store(keySetEntity);
    }

    @Override
    public void archive() {
        KeysetConfiguration keysetConfiguration = getConfiguration();
        VaultClient<KeySetEntity> client = getClient();

        client.archive(keysetConfiguration.getId());
    }

    @Override
    public void delete() {
        KeysetConfiguration keysetConfiguration = getConfiguration();
        VaultClient<KeySetEntity> client = getClient();

        client.delete(keysetConfiguration.getId());
    }

    private MintEntity getMint(KeysetConfiguration keysetConfiguration) {
        VaultClient<MintEntity> mintEntityVaultClient = new VaultClient<>(MintEntity.class);
        return mintEntityVaultClient.retrieve(keysetConfiguration.getMint().getId());
    }

    private Set<KeyEntity> getKeys(KeysetConfiguration keysetConfiguration) {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        return keyVaultClient.getKeysByUnit(keysetConfiguration.getUnit());
    }

    @Override
    public String retrieve(boolean archived) throws CashuErrorException {
        KeySetEntity keySetEntity = retrieveEntity();
        return keySetEntity.isArchived() != archived ? null : keySetEntity.getId().toString();
    }

    @Override
    protected KeySetEntity retrieveEntity() throws CashuErrorException {
        KeysetConfiguration keysetConfiguration = getConfiguration();
        VaultClient<KeySetEntity> client = getClient();

        KeySetEntity keySetEntity = client.retrieve(keysetConfiguration.getId());
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }
        return keySetEntity;
    }

    public static KeySet load(@NonNull KeysetConfiguration keysetConfiguration, boolean archive) throws CashuErrorException {
        KeyConfiguration keyConfiguration = new KeyConfiguration(keysetConfiguration);
        Keys keys = DBKeyVault.load(keyConfiguration, archive);
        return KeySet.builder().id(keysetConfiguration.getId()).keys(keys).build();
    }
}
