package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.Set;

/**
 * REST client for interacting with key-related endpoints in the vault
 * service.
 */
@Slf4j
public class KeyVaultClient extends VaultClient<KeyEntity> {

    /**
     * Creates a client instance for {@link KeyEntity} operations using the
     * default base URL resolution logic.
     */
    public KeyVaultClient() {
        super(KeyEntity.class);
    }

    /**
     * Retrieves all keys for a given unit.
     *
     * @param unit monetary unit code
     * @return set of matching key entities
     */
    public Set<KeyEntity> getKeysByUnit(String unit) {
        log.info("GET {}/vault/key/unit/{}", getBaseUrl(), unit);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/unit/" + unit, Set.class);
    }

    /**
     * Retrieves all keys that belong to the specified key set.
     *
     * @param id identifier of the key set
     * @return set of key entities associated with the key set
     */
    public Set<KeyEntity> getKeysByKeySetId(String id) {
        log.info("GET {}/vault/key/keyset/{}", getBaseUrl(), id);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/keyset/" + id, Set.class);
    }

    /**
     * Retrieves a key by its private key value.
     *
     * @param privateKey private key string
     * @return matching key entity or {@code null} if none exists
     */
    public KeyEntity getByPrivateKey(String privateKey) {
        log.info("GET {}/vault/key/private/{}", getBaseUrl(), privateKey);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/private/" + privateKey, KeyEntity.class);
    }
}
