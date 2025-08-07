package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

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
