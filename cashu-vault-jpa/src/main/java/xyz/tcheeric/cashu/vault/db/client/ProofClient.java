package xyz.tcheeric.cashu.vault.db.client;

import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public class ProofClient extends VaultClient<ProofEntity> {

    public ProofClient() {
        super(ProofEntity.class);
    }

    public ProofEntity getByMintIdAndSecret(String mintId, String secret) {
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        Set<ProofEntity> optionalProofEntities = restTemplate.getForObject(getBaseUrl() + "/vault/proof/mint/" + mintId, Set.class);
        return optionalProofEntities.stream()
                .filter(proofEntity -> proofEntity.getSecret().equals(secret))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proof not found for mintId: " + mintId + " and secret: " + secret));
    }

    public ProofEntity getByMintAndAmount(String mintId, Integer amount) {
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        Set<ProofEntity> optionalProofEntities = restTemplate.getForObject(getBaseUrl() + "/vault/proof/mint/" + mintId, Set.class);
        return optionalProofEntities.stream()
                .filter(proofEntity -> proofEntity.getAmount().equals(amount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proof not found for mintId: " + mintId + " and amount: " + amount));
    }

    public ProofEntity getByMintAndUnblindedSignature(String mintId, String unblindedSignature) {
        log.info("GET {}/vault/proof/mint/{}", getBaseUrl(), mintId);
        Set<ProofEntity> optionalProofEntities = restTemplate.getForObject(getBaseUrl() + "/vault/proof/mint/" + mintId, Set.class);
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
