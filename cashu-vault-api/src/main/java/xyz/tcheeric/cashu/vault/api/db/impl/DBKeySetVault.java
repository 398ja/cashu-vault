package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.Set;

public class DBKeySetVault extends DBVault<KeySetEntity> {

    public DBKeySetVault(KeySetEntity entity) {
        super(entity, new CashuVaultApplication().vaultKeySetClient());
    }

    @Override
    public void store() {
        KeySetEntity keySetEntity = getEntity();
        VaultClient<KeySetEntity> client = getClient();

        keySetEntity.setMint(getMint(keySetEntity));

        client.store(keySetEntity);
    }

    @Override
    public void archive() {
        KeySetEntity keySetEntity = getEntity();
        VaultClient<KeySetEntity> client = getClient();

        client.archive(keySetEntity.getId().toString());
    }

    @Override
    public void delete() {
        KeySetEntity keySetEntity = getEntity();
        VaultClient<KeySetEntity> client = getClient();

        client.delete(keySetEntity.getId().toString());
    }

    private MintEntity getMint(KeySetEntity keySetEntity) {
        VaultClient<MintEntity> mintEntityVaultClient = new VaultClient<>(MintEntity.class);
        return mintEntityVaultClient.retrieve(keySetEntity.getMint().getId().toString());
    }

    private Set<KeyEntity> getKeys(KeySetEntity keySetEntity) {
        KeyVaultClient keyVaultClient = new KeyVaultClient();
        return keyVaultClient.getKeysByUnit(keySetEntity.getUnit());
    }


    @Override
    protected KeySetEntity retrieveEntity(String id) throws CashuErrorException {
        VaultClient<KeySetEntity> client = getClient();
        KeySetEntity keySetEntity = client.retrieve(id);
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }
        return keySetEntity;
    }

    public static DBKeySetVault retrieveKeySet(@NonNull String id) throws CashuErrorException {
        DBKeySetVault keySetVault = new DBKeySetVault(null);
        return new DBKeySetVault(keySetVault.retrieveEntity(id));
    }

    public static KeySet load(@NonNull KeySetEntity keySetEntity, boolean archive) throws CashuErrorException {
        Keys keys = new Keys();
        keySetEntity.getKeys().forEach(k -> keys.put(k.getAmount(), PublicKey.fromString(k.getPrivateKey())));
        return KeySet.builder().id(keySetEntity.getKeySetId()).keys(keys).build();
    }
}
