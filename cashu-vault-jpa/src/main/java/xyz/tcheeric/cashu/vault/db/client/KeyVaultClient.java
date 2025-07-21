package xyz.tcheeric.cashu.vault.db.client;

import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class KeyVaultClient extends VaultClient<KeyEntity> {

    public KeyVaultClient() {
        super(KeyEntity.class);
    }

    public Set<KeyEntity> getKeysByUnit(String unit) {
        log.info("GET {}/vault/key/unit/{}", getBaseUrl(), unit);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/unit/" + unit, Set.class);
    }

    public Set<KeyEntity> getKeysByKeySetId(String id) {
        log.info("GET {}/vault/key/keyset/{}", getBaseUrl(), id);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/keyset/" + id, Set.class);
    }

    public KeyEntity getByPrivateKey(String privateKey) {
        log.info("GET {}/vault/key/private/{}", getBaseUrl(), privateKey);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/private/" + privateKey, KeyEntity.class);
    }
}
