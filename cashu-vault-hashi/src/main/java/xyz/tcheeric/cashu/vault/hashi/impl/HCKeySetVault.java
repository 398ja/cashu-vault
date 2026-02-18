package xyz.tcheeric.cashu.vault.hashi.impl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.util.Map;

@Slf4j
public final class HCKeySetVault extends DBVault<KeySetEntity> {

    private final HashiVaultClient hashiClient;

    public HCKeySetVault(HashiVaultClient hashiClient) {
        super(VaultClientFactory.getClient(KeySetEntity.class));
        this.hashiClient = hashiClient;
    }

    public HCKeySetVault(VaultClient<KeySetEntity> client, HashiVaultClient hashiClient) {
        super(client);
        this.hashiClient = hashiClient;
    }

    @Override
    public KeySetEntity store(KeySetEntity keySetEntity) throws CashuErrorException {
        keySetEntity.setMint(getMint(keySetEntity));
        return client.store(keySetEntity);
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

    /**
     * Loads keys for a keyset, enriching private keys from HashiCorp Vault when vault_path is present.
     */
    public static KeySet load(@NonNull KeySetEntity keySetEntity, boolean archive,
                              HashiVaultClient hashiClient) throws CashuErrorException {
        Keys keys = new Keys();
        keySetEntity.getKeys().forEach(k -> {
            enrichWithVaultSecret(k, hashiClient);
            keys.put(k.getAmount(), PublicKey.fromString(k.getPrivateKey()));
        });
        return KeySet.builder().id(keySetEntity.getKeySetId()).keys(keys).build();
    }

    private static void enrichWithVaultSecret(KeyEntity entity, HashiVaultClient hashiClient) {
        if (entity.getVaultPath() != null && entity.getPrivateKey() == null) {
            Map<String, Object> data = hashiClient.getSecret(entity.getVaultPath());
            if (data != null) {
                entity.setPrivateKey((String) data.get("private_key"));
            }
        }
    }

    private MintEntity getMint(KeySetEntity keySetEntity) {
        VaultClient<MintEntity> mintEntityVaultClient = VaultClientFactory.getClient(MintEntity.class);
        return mintEntityVaultClient.retrieve(keySetEntity.getMint().getId().toString());
    }
}
