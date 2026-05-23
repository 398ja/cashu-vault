package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Collection;
import java.util.List;
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
     * Constraint name for unique (mint_id, secret) - must match migration.
     */
    String CONSTRAINT_MINT_SECRET = "uk_proof_mint_secret";

    /**
     * Constraint name for unique (mint_id, C) - must match migration.
     */
    String CONSTRAINT_MINT_COMMITMENT = "uk_proof_mint_commitment";

    /**
     * Stores proof if not already present (duplicate detection).
     * Uses the unique constraint on (mint_id, secret) to prevent duplicates.
     *
     * @param proof proof to store
     * @return InsertResult indicating success or duplicate
     * @throws DataIntegrityViolationException for non-duplicate constraint violations
     *         (e.g., NOT NULL, FK violations)
     */
    default InsertResult insertIfNotExists(ProofEntity proof) {
        UUID mintId = proof.getMint() != null ? proof.getMint().getId() : null;
        String secret = proof.getSecret();

        // Validate required fields - throw early for null mint (NOT NULL constraint)
        if (mintId == null) {
            throw new DataIntegrityViolationException("Proof must have a mint associated");
        }

        // Check for existing proof by mint and secret
        if (secret != null && existsByMint_IdAndSecret(mintId, secret)) {
            return new InsertResult(false, null, "Duplicate proof: same secret already exists for mint");
        }

        try {
            ProofEntity saved = save(proof);
            return new InsertResult(true, saved, null);
        } catch (DataIntegrityViolationException e) {
            // Only treat as duplicate if it's a unique constraint violation on our duplicate-detection constraints
            if (isDuplicateConstraintViolation(e)) {
                return new InsertResult(false, null, "Duplicate proof detected by constraint: " + e.getMessage());
            }
            // Rethrow other integrity violations (NOT NULL, FK, etc.)
            throw e;
        }
    }

    /**
     * Checks if the exception is a unique constraint violation for duplicate detection.
     * Only returns true for violations of uk_proof_mint_secret or uk_proof_mint_commitment.
     */
    private static boolean isDuplicateConstraintViolation(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message == null) {
            Throwable cause = e.getCause();
            message = cause != null ? cause.getMessage() : "";
        }
        String lowerMessage = message.toLowerCase();

        // Check for our specific unique constraint names (case-insensitive)
        return lowerMessage.contains(CONSTRAINT_MINT_SECRET.toLowerCase())
                || lowerMessage.contains(CONSTRAINT_MINT_COMMITMENT.toLowerCase())
                // Also check for generic unique violation patterns that mention our columns
                || (lowerMessage.contains("unique") && lowerMessage.contains("secret"))
                || (lowerMessage.contains("unique") && lowerMessage.contains("mint_id"));
    }

    /**
     * Result of an insert operation with duplicate detection.
     */
    record InsertResult(boolean stored, ProofEntity proof, String duplicateReason) {
        public boolean isDuplicate() {
            return !stored;
        }
    }

    // ---------------------------------------------------------------
    // cashu-mint spec 002 T011 — melt saga binding helpers
    // ---------------------------------------------------------------

    /**
     * Spec 002 T011 — atomically marks the named proofs PENDING and binds
     * them to a single melt saga. Exclusivity is enforced by the UPDATE
     * predicate itself ({@code state='UNSPENT' AND melt_saga_id IS NULL}):
     * a concurrent claim on the same row sees rowcount=0 (no exception);
     * the losing caller bails out by comparing the returned count against
     * {@code proofIds.size()}.
     *
     * <p>Returns the number of rows updated. A return value less than
     * {@code proofIds.size()} means partial application — some proofs
     * already SPENT, already held by another saga, or missing.
     *
     * @param proofIds    Y-coordinate secrets of the proofs to claim
     * @param meltSagaId  saga id that will hold the proofs
     * @param mintId      mint that owns the proofs (scopes the update)
     * @return number of rows actually transitioned UNSPENT → PENDING
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE proof p SET p.state = 'PENDING', p.meltSagaId = :meltSagaId "
            + "WHERE p.mint.id = :mintId "
            + "AND p.secret IN :proofIds "
            + "AND p.state = 'UNSPENT' "
            + "AND p.meltSagaId IS NULL")
    int markPending(@Param("proofIds") Collection<String> proofIds,
                    @Param("meltSagaId") String meltSagaId,
                    @Param("mintId") UUID mintId);

    /**
     * Spec 002 T011 — clears {@code melt_saga_id} on the named proofs.
     * Called when a saga transitions out of PROOFS_HELD: either to
     * COMPLETED (also flips state to SPENT — see
     * {@link #commitSpent}), to FAILED (rolls back to UNSPENT — see
     * {@link #refundToUnspent}), or operator-initiated cleanup.
     *
     * @return number of rows actually cleared
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE proof p SET p.meltSagaId = NULL "
            + "WHERE p.meltSagaId = :meltSagaId")
    int clearMeltSaga(@Param("meltSagaId") String meltSagaId);

    /**
     * Spec 002 T011 — commits a saga's PENDING proofs as SPENT and clears
     * the saga binding atomically. Used by {@code MeltTask} on the
     * happy-path transition {@code PAYMENT_SENT → COMPLETED}.
     *
     * @return number of rows actually transitioned PENDING → SPENT
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE proof p SET p.state = 'SPENT', p.meltSagaId = NULL "
            + "WHERE p.meltSagaId = :meltSagaId "
            + "AND p.state = 'PENDING'")
    int commitSpent(@Param("meltSagaId") String meltSagaId);

    /**
     * Spec 002 T011 — refunds a saga's PENDING proofs back to UNSPENT and
     * clears the saga binding atomically. Used by {@code MeltTask} on the
     * compensation transition {@code PROOFS_HELD → FAILED} (and by the
     * scheduled reconciler's PROOFS_HELD TTL sweep).
     *
     * @return number of rows actually transitioned PENDING → UNSPENT
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE proof p SET p.state = 'UNSPENT', p.meltSagaId = NULL "
            + "WHERE p.meltSagaId = :meltSagaId "
            + "AND p.state = 'PENDING'")
    int refundToUnspent(@Param("meltSagaId") String meltSagaId);

    /**
     * Spec 002 T011 — operator-visible enumeration of proofs currently
     * held by a saga. Backs the admin saga query endpoint + the daily
     * SC-002 reconciliation invariant ({@code every COMPLETED saga has 0
     * still-held proofs}).
     */
    @Query("SELECT p FROM proof p WHERE p.meltSagaId = :meltSagaId")
    List<ProofEntity> findByMeltSagaId(@Param("meltSagaId") String meltSagaId);
}