package xyz.tcheeric.cashu.vault.db.client;

import xyz.tcheeric.cashu.vault.db.config.VaultBaseProperties;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.Set;

public class KeyVaultClient extends VaultClient<KeyEntity> {

    public KeyVaultClient() {
        super(KeyEntity.class);
    }

    public KeyVaultClient(VaultBaseProperties properties) {
        super(KeyEntity.class, properties);
    }

    public Set<KeyEntity> getKeysByUnit(String unit) {
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/unit/" + unit, Set.class);
    }

    public Set<KeyEntity> getKeysByKeySetId(String id) {
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/keyset/" + id, Set.class);
    }

    public KeyEntity getByPrivateKey(String privateKey) {
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/private/" + privateKey, KeyEntity.class);
    }
}
