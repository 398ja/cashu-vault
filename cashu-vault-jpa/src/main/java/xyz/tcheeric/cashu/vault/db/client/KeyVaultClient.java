package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.Set;

@Slf4j
public class KeyVaultClient extends VaultClient<KeyEntity> {

    public KeyVaultClient() {
        super(KeyEntity.class);
    }

    public Set<KeyEntity> getKeysByUnit(String unit) {
        log.info("GET {}/vault/key/unit/{}", getBaseUrl(), unit);
        ResponseEntity<Set<KeyEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/key/unit/" + unit,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<KeyEntity>>() {
                }
        );
        return response.getBody();
    }

    public Set<KeyEntity> getKeysByKeySetId(String id) {
        log.info("GET {}/vault/key/keyset/{}", getBaseUrl(), id);
        ResponseEntity<Set<KeyEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/key/keyset/" + id,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<KeyEntity>>() {
                }
        );
        return response.getBody();
    }

    public KeyEntity getByPrivateKey(String privateKey) {
        log.info("GET {}/vault/key/private/{}", getBaseUrl(), privateKey);
        return restTemplate.getForObject(getBaseUrl() + "/vault/key/private/" + privateKey, KeyEntity.class);
    }
}
