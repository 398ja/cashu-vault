package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for {@link ProofEntity} storage operations.
 */
public interface ProofRepository extends JpaRepository<ProofEntity, UUID> {

    /**
     * Finds a proof by its secret value.
     *
     * @param secret proof secret
     * @return optional proof entity
     */
    Optional<ProofEntity> findBySecret(String secret);

    /**
     * Finds proofs belonging to the specified mint.
     *
     * @param id mint identifier
     * @return optional set of proof entities
     */
    Optional<Set<ProofEntity>> findByMint_Id(UUID id);

    /**
     * Finds a proof by the mint it belongs to and its secret.
     *
     * @param id     mint identifier
     * @param secret proof secret
     * @return optional proof entity
     */
    Optional<ProofEntity> findByMint_IdAndSecret(UUID id, String secret);

    /**
     * Finds proofs by the mint they belong to and their amount.
     *
     * @param id     mint identifier
     * @param amount proof amount
     * @return optional set of proof entities
     */
    Optional<Set<ProofEntity>> findByMint_IdAndAmount(UUID id, Integer amount);

    /**
     * Finds a proof by the mint it belongs to and its unblinded signature.
     *
     * @param id                 mint identifier
     * @param unblindedSignature unblinded signature value
     * @return optional proof entity
     */
    Optional<ProofEntity> findByMint_IdAndUnblindedSignature(UUID id, String unblindedSignature);

    /**
     * Finds proofs by their state.
     *
     * @param state proof state
     * @return optional set of proofs in that state
     */
    Optional<Set<ProofEntity>> findByStateIgnoreCase(String state);

    /**
     * Checks if a proof exists for the given mint and secret.
     *
     * @param mintId mint identifier
     * @param secret proof secret
     * @return true if proof exists
     */
    boolean existsByMint_IdAndSecret(UUID mintId, String secret);

    /**
     * Checks if a proof exists for the given mint and commitment (C).
     *
     * @param mintId             mint identifier
     * @param unblindedSignature commitment value (C)
     * @return true if proof exists
     */
    boolean existsByMint_IdAndUnblindedSignature(UUID mintId, String unblindedSignature);

    /**
     * Finds a proof by its fingerprint.
     *
     * @param fingerprint SHA-256 fingerprint
     * @return optional proof entity
     */
    Optional<ProofEntity> findByFingerprint(String fingerprint);

    /**
     * Checks if a proof exists with the given fingerprint.
     *
     * @param fingerprint SHA-256 fingerprint
     * @return true if proof exists
     */
    boolean existsByFingerprint(String fingerprint);

    /**
     * Stores proof if not already present (duplicate detection).
     * Uses the unique constraint on (mint_id, secret) to prevent duplicates.
     *
     * @param proof proof to store
     * @return InsertResult indicating success or duplicate
     */
    default InsertResult insertIfNotExists(ProofEntity proof) {
        UUID mintId = proof.getMint() != null ? proof.getMint().getId() : null;
        String secret = proof.getSecret();

        // Check for existing proof by mint and secret
        if (mintId != null && secret != null && existsByMint_IdAndSecret(mintId, secret)) {
            return new InsertResult(false, null, "Duplicate proof: same secret already exists for mint");
        }

        try {
            ProofEntity saved = save(proof);
            return new InsertResult(true, saved, null);
        } catch (DataIntegrityViolationException e) {
            // Constraint violation - concurrent duplicate insertion
            return new InsertResult(false, null, "Duplicate proof detected by constraint: " + e.getMessage());
        }
    }

    /**
     * Result of an insert operation with duplicate detection.
     */
    record InsertResult(boolean stored, ProofEntity proof, String duplicateReason) {
        public boolean isDuplicate() {
            return !stored;
        }
    }
}