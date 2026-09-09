package xyz.tcheeric.cashu.vault.db.client;

import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.vault.db.log.SecretLogId;
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
        log.info("GET {}/vault/proof/mint/{}/secret/{}", getBaseUrl(), mintId, SecretLogId.of(secret));
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
        log.info("GET {}/vault/proof/mint/{}/signature/{}", getBaseUrl(), mintId, SecretLogId.of(unblindedSignature));
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
        log.info("GET {}/vault/proof/secret/{}", getBaseUrl(), SecretLogId.of(secret));
        return restTemplate.getForObject(getBaseUrl() + "/vault/proof/secret/" + secret, ProofEntity.class);
    }

    // ---------------------------------------------------------------
    // cashu-mint spec 002 T011 — melt-saga binding REST calls
    // ---------------------------------------------------------------

    /**
     * Spec 005 — atomic insert-or-claim. Submits already Y-normalised
     * {@link ProofEntity} rows; the vault inserts or claims each in
     * state PENDING bound to {@code holdId} and returns the total
     * bound count.
     *
     * <p>Callers compare the returned count against {@code proofs.size()}
     * and abort (releasing any partial holds via {@link #refund}) on
     * mismatch, before any external payment is initiated.
     */
    public int insertOrClaimForHold(String mintId, String holdId, java.util.List<ProofEntity> proofs) {
        log.info("POST {}/vault/proof/mint/{}/hold/{}/insert-or-claim proofs={}",
                getBaseUrl(), mintId, holdId, proofs.size());
        Integer bound = restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/hold/" + holdId + "/insert-or-claim",
                proofs,
                Integer.class);
        return bound == null ? 0 : bound;
    }

    /**
     * cashu-mint spec 002 T011 — atomically marks proofs PENDING and
     * binds them to the named melt saga. The vault enforces the
     * exclusivity at the row level via the application-level CAS in
     * {@code ProofRepository.markPending}.
     *
     * @return number of rows actually transitioned UNSPENT → PENDING
     */
    public int markPending(String mintId, String holdId, java.util.List<String> proofSecrets) {
        log.info("POST {}/vault/proof/mint/{}/hold/{}/mark-pending proofs={}",
                getBaseUrl(), mintId, holdId, proofSecrets.size());
        Integer updated = restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/mint/" + mintId + "/hold/" + holdId + "/mark-pending",
                proofSecrets,
                Integer.class);
        return updated == null ? 0 : updated;
    }

    /**
     * cashu-mint spec 002 T011 — commits a saga's PENDING proofs as
     * SPENT and clears the {@code hold_id} binding.
     */
    public int commitSpent(String holdId) {
        log.info("POST {}/vault/proof/hold/{}/commit-spent", getBaseUrl(), holdId);
        Integer updated = restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/hold/" + holdId + "/commit-spent",
                null, Integer.class);
        return updated == null ? 0 : updated;
    }

    /**
     * cashu-mint spec 002 T011 — refunds a saga's PENDING proofs back
     * to UNSPENT and clears the {@code hold_id} binding.
     */
    public int refund(String holdId) {
        log.info("POST {}/vault/proof/hold/{}/refund", getBaseUrl(), holdId);
        Integer updated = restTemplate.postForObject(
                getBaseUrl() + "/vault/proof/hold/" + holdId + "/refund",
                null, Integer.class);
        return updated == null ? 0 : updated;
    }
}
