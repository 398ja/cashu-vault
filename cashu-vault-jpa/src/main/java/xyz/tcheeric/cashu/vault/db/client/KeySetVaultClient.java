package xyz.tcheeric.cashu.vault.db.client;

import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class KeySetVaultClient extends VaultClient<KeySetEntity> {

    public KeySetVaultClient() {
        super(KeySetEntity.class);
    }

    public KeySetEntity getByKeySetId(String keySetId) {
        log.info("GET {}/vault/keyset/id/{}", getBaseUrl(), keySetId);
        return restTemplate.getForObject(getBaseUrl() + "/vault/keyset/id/" + keySetId, KeySetEntity.class);
    }

    public Set<KeySetEntity> getByUnit(String unit) {
        log.info("GET {}/vault/keyset/unit/{}", getBaseUrl(), unit);
        Set<KeySetEntity> keySetEntities = restTemplate.getForObject(getBaseUrl() + "/vault/keyset/unit/" + unit, Set.class);
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for unit: " + unit);
        }
        return keySetEntities;
    }

    public Set<KeySetEntity> getByMintId(String mintId) {
        log.info("GET {}/vault/keyset/mint/{}", getBaseUrl(), mintId);
        Set<KeySetEntity> keySetEntities = restTemplate.getForObject(getBaseUrl() + "/vault/keyset/mint/" + mintId, Set.class);
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for mintId: " + mintId);
        }
        return keySetEntities;
    }
}
