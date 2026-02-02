package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.Set;

public final class DBKeySetVault extends DBVault<KeySetEntity> {

    public DBKeySetVault() {
        this(VaultClientFactory.getClient(KeySetEntity.class));
    }

    public DBKeySetVault(VaultClient<KeySetEntity> client) {
        super(client);
    }

    @Override
    public KeySetEntity store(KeySetEntity keySetEntity) throws CashuErrorException {
        keySetEntity.setMint(getMint(keySetEntity));
        return client.store(keySetEntity);
    }

    private MintEntity getMint(KeySetEntity keySetEntity) {
        VaultClient<MintEntity> mintEntityVaultClient = VaultClientFactory.getClient(MintEntity.class);
        return mintEntityVaultClient.retrieve(keySetEntity.getMint().getId().toString());
    }

    private Set<KeyEntity> getKeys(KeySetEntity keySetEntity) {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        return keyVaultClient.getKeysByUnit(keySetEntity.getUnit());
    }

    @Override
    protected KeySetEntity retrieveEntity(String id) throws CashuErrorException {
        KeySetEntity keySetEntity = client.retrieve(id);
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }
        return keySetEntity;
    }

    public KeySetEntity retrieveByMintIdAndUnit(@NonNull String mintId, @NonNull String unit) throws CashuErrorException {
        KeySetVaultClient ksClient = VaultClientFactory.keySetClient();
        KeySetEntity keySetEntity = ksClient.getByMintId(mintId).stream()
                .filter(k -> k.getUnit().equals(unit))
                .findFirst()
                .orElse(null);
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found for mintId: " + mintId + " and unit: " + unit);
        }
        return keySetEntity;
    }

    public static KeySet load(@NonNull KeySetEntity keySetEntity, boolean archive) throws CashuErrorException {
        Keys keys = new Keys();
        keySetEntity.getKeys().forEach(k -> keys.put(k.getAmount(), PublicKey.fromString(k.getPrivateKey())));
        return KeySet.builder().id(keySetEntity.getKeySetId()).keys(keys).build();
    }
}
