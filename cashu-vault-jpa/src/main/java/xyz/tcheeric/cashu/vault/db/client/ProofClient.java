package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Set;

@Slf4j
public class ProofClient extends VaultClient<ProofEntity> {

    public ProofClient() {
        super(ProofEntity.class);
    }

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

    public ProofEntity getBySecret(String secret) {
        log.info("GET {}/vault/proof/secret/{}", getBaseUrl(), secret);
        return restTemplate.getForObject(getBaseUrl() + "/vault/proof/secret/" + secret, ProofEntity.class);
    }
}
