package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
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
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        ResponseEntity<Set<ProofEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/proof/mint/" + mintId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<ProofEntity>>() {
                }
        );
        Set<ProofEntity> optionalProofEntities = response.getBody();
        if (optionalProofEntities == null || optionalProofEntities.isEmpty()) {
            throw new IllegalArgumentException("No proofs found for mintId: " + mintId);
        }
        return optionalProofEntities.stream()
                .filter(proofEntity -> proofEntity.getSecret().equals(secret))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proof not found for mintId: " + mintId + " and secret: " + secret));
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
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        ResponseEntity<Set<ProofEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/proof/mint/" + mintId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<ProofEntity>>() {
                }
        );
        Set<ProofEntity> optionalProofEntities = response.getBody();
        if (optionalProofEntities == null || optionalProofEntities.isEmpty()) {
            throw new IllegalArgumentException("No proofs found for mintId: " + mintId);
        }
        return optionalProofEntities.stream()
                .filter(proofEntity -> proofEntity.getAmount().equals(amount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proof not found for mintId: " + mintId + " and amount: " + amount));
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
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        ResponseEntity<Set<ProofEntity>> response = restTemplate.exchange(
                getBaseUrl() + "/vault/proof/mint/" + mintId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<ProofEntity>>() {
                }
        );
        Set<ProofEntity> optionalProofEntities = response.getBody();
        if (optionalProofEntities == null || optionalProofEntities.isEmpty()) {
            throw new IllegalArgumentException("No proofs found for mintId: " + mintId);
        }
        return optionalProofEntities.stream()
                .filter(proofEntity -> proofEntity.getUnblindedSignature().equals(unblindedSignature))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proof not found for mintId: " + mintId + " and unblindedSignature: " + unblindedSignature));
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
