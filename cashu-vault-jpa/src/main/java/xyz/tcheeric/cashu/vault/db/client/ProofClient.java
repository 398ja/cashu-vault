package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneResponse;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Map;
import java.util.Set;

/**
 * REST client providing lookup utilities for {@link ProofEntity} resources.
 */
@Slf4j
public class ProofClient extends VaultClient<ProofEntity> {

    public ProofClient() {
        super(ProofEntity.class);
    }

    /**
     * Finds a proof for the given mint and secret.
     */
    public ProofEntity getByMintIdAndSecret(String mintId, String secret) {
        log.info("GET {}/vault/proof/mint/{}/secret/{}", getBaseUrl(), mintId, secret);
        return restTemplate.getForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/secret/" + secret,
                ProofEntity.class);
    }

    /**
     * Finds a proof for the given mint and amount.
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
     */
    public ProofEntity getByMintAndUnblindedSignature(String mintId, String unblindedSignature) {
        log.info("GET {}/vault/proof/mint/{}/signature/{}", getBaseUrl(), mintId, unblindedSignature);
        return restTemplate.getForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/signature/" + unblindedSignature,
                ProofEntity.class);
    }

    /**
     * Admin tombstone — replaces the removed DELETE path (spec 001 / FR-001, FR-002).
     */
    public TombstoneResponse tombstone(String mintId, String secret, String reason, boolean force) {
        log.info("POST {}/vault/proof/mint/{}/secret/{}/tombstone force={}", getBaseUrl(), mintId, secret, force);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("reason", reason, "force", force), headers);
        return restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/secret/" + secret + "/tombstone",
                req, TombstoneResponse.class);
    }

    /**
     * State-only transition — REPLACES re-POSTing the full entity to /vault/proof (FR-008).
     */
    public ProofEntity transitionState(String mintId, String secret, String toState) {
        log.info("POST {}/vault/proof/mint/{}/secret/{}/state to={}", getBaseUrl(), mintId, secret, toState);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("to", toState), headers);
        return restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/secret/" + secret + "/state",
                req, ProofEntity.class);
    }
}
