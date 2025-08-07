package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.util.Set;

/**
 * REST client for operations on {@link KeySetEntity} resources.
 */
@Slf4j
public class KeySetVaultClient extends VaultClient<KeySetEntity> {

    /**
     * Creates a new client for key set operations using the default base URL.
     */
    public KeySetVaultClient() {
        super(KeySetEntity.class);
    }

    /**
     * Retrieves a key set by its key set identifier.
     *
     * @param keySetId identifier of the key set
     * @return matching key set entity or {@code null} if not found
     */
    public KeySetEntity getByKeySetId(String keySetId) {
        log.info("GET {}/vault/keyset/id/{}", getBaseUrl(), keySetId);
        return restTemplate.getForObject(getBaseUrl() + "/vault/keyset/id/" + keySetId, KeySetEntity.class);
    }

    /**
     * Retrieves key sets by unit.
     *
     * @param unit monetary unit code
     * @return set of key sets for the unit
     * @throws IllegalArgumentException if no key set exists for the unit
     */
    public Set<KeySetEntity> getByUnit(String unit) {
        log.info("GET {}/vault/keyset/unit/{}", getBaseUrl(), unit);
        ResponseEntity<Set<KeySetEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/keyset/unit/" + unit,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<KeySetEntity>>() {
                }
        );
        Set<KeySetEntity> keySetEntities = response.getBody();
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for unit: " + unit);
        }
        return keySetEntities;
    }

    /**
     * Retrieves key sets by mint identifier.
     *
     * @param mintId mint identifier
     * @return set of key sets for the mint
     * @throws IllegalArgumentException if no key set exists for the mint ID
     */
    public Set<KeySetEntity> getByMintId(String mintId) {
        log.info("GET {}/vault/keyset/mint/{}", getBaseUrl(), mintId);
        ResponseEntity<Set<KeySetEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/keyset/mint/" + mintId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<KeySetEntity>>() {
                }
        );
        Set<KeySetEntity> keySetEntities = response.getBody();
        if (keySetEntities == null || keySetEntities.isEmpty()) {
            throw new IllegalArgumentException("No KeySet found for mintId: " + mintId);
        }
        return keySetEntities;
    }
}
