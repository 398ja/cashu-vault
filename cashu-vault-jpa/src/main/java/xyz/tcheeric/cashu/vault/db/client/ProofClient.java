package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Set;

/**
 * REST client providing lookup utilities for {@link ProofEntity} resources.
 */
@Slf4j
public class ProofClient extends VaultClient<ProofEntity> {

    /**
     * Creates a new client instance for proof operations using the default base URL.
     */
    public ProofClient() {
        super(ProofEntity.class);
    }

    /**
     * Finds a proof for the given mint and secret.
     *
     * @param mintId identifier of the mint
     * @param secret proof secret
     * @return matching proof entity
     * @throws IllegalArgumentException if no proof matches the criteria
     */
    public ProofEntity getByMintIdAndSecret(String mintId, String secret) {
        log.info("GET {}/vault/proof/mint/{}/secret/{}", getBaseUrl(), mintId, secret);
        ProofEntity proofEntity = restTemplate.getForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/secret/" + secret,
                ProofEntity.class);
        return proofEntity;
    }

    /**
     * Finds a proof for the given mint and amount.
     *
     * @param mintId identifier of the mint
     * @param amount proof amount
     * @return matching proof entity
     * @throws IllegalArgumentException if no proof matches the criteria
     */
    public ProofEntity getByMintAndAmount(String mintId, Integer amount) {
        log.info("GET {}/vault/proof/mint/{}/amount/{}", getBaseUrl(), mintId, amount);
        ResponseEntity<Set<ProofEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/amount/" + amount,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<ProofEntity>>() {
                }
        );
        Set<ProofEntity> optionalProofEntities = response.getBody();
        if (optionalProofEntities == null || optionalProofEntities.isEmpty()) {
            return null;
        }
        return optionalProofEntities.iterator().next();
    }

    /**
     * Finds a proof for the given mint and unblinded signature.
     *
     * @param mintId             identifier of the mint
     * @param unblindedSignature unblinded signature value
     * @return matching proof entity
     * @throws IllegalArgumentException if no proof matches the criteria
     */
    public ProofEntity getByMintAndUnblindedSignature(String mintId, String unblindedSignature) {
        log.info("GET {}/vault/proof/mint/{}/signature/{}", getBaseUrl(), mintId, unblindedSignature);
        ProofEntity proofEntity = restTemplate.getForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/signature/" + unblindedSignature,
                ProofEntity.class);
        return proofEntity;
    }

    /**
     * Retrieves a proof by its secret value.
     *
     * @param secret proof secret
     * @return matching proof entity or {@code null} if none exists
     */
    public ProofEntity getBySecret(String secret) {
        log.info("GET {}/vault/proof/secret/{}", getBaseUrl(), secret);
        return restTemplate.getForObject(getBaseUrl() + "/vault/proof/secret/" + secret, ProofEntity.class);
    }
}
