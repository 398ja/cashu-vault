package xyz.tcheeric.cashu.vault.db.client;

import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.util.Set;

public class KeySetVaultClient extends VaultClient<KeySetEntity> {

    public KeySetVaultClient() {
        super(KeySetEntity.class);
    }

    public KeySetEntity getByKeySetId(String keySetId) {
        return restTemplate.getForObject(getBaseUrl() + "/vault/keyset/id/" + keySetId, KeySetEntity.class);
    }

    public Set<KeySetEntity> getByUnit(String unit) {
        Set<KeySetEntity> keySetEntities = restTemplate.getForObject(getBaseUrl() + "/vault/keyset/unit/" + unit, Set.class);
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for unit: " + unit);
        }
        return keySetEntities;
    }

    public Set<KeySetEntity> getByMintId(String mintId) {
        Set<KeySetEntity> keySetEntities = restTemplate.getForObject(getBaseUrl() + "/vault/keyset/mint/" + mintId, Set.class);
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for mintId: " + mintId);
        }
        return keySetEntities;
    }
}
