package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for {@link ProofEntity} storage operations.
 *
 * Spec 001 / Constitution Principle I — proofs are append-only. All {@code JpaRepository}
 * mutators that physically remove rows are overridden to throw
 * {@link UnsupportedOperationException} (FR-001). Identity-column lookup by
 * {@code secret} alone has been removed (FR-005) — every read MUST carry a {@code mint_id}.
 */
public interface ProofRepository extends JpaRepository<ProofEntity, UUID> {

    /**
     * Finds proofs belonging to the specified mint.
     */
    Optional<Set<ProofEntity>> findByMint_Id(UUID id);

    /**
     * Finds a proof by the mint it belongs to and its secret.
     */
    Optional<ProofEntity> findByMint_IdAndSecret(UUID id, String secret);

    /**
     * Finds proofs by the mint they belong to and their amount.
     */
    Optional<Set<ProofEntity>> findByMint_IdAndAmount(UUID id, Integer amount);

    /**
     * Finds a proof by the mint it belongs to and its unblinded signature.
     */
    Optional<ProofEntity> findByMint_IdAndUnblindedSignature(UUID id, String unblindedSignature);

    /**
     * Finds proofs by their state (admin-only operational scan).
     */
    Optional<Set<ProofEntity>> findByStateIgnoreCase(String state);

    /**
     * Checks if a proof exists for the given mint and secret.
     */
    boolean existsByMint_IdAndSecret(UUID mintId, String secret);

    /**
     * Checks if a proof exists for the given mint and commitment (C).
     */
    boolean existsByMint_IdAndUnblindedSignature(UUID mintId, String unblindedSignature);

    /**
     * Finds a proof by its fingerprint (fingerprint embeds mint_id via SHA-256 — effectively mint-scoped).
     */
    Optional<ProofEntity> findByFingerprint(String fingerprint);

    /**
     * Checks if a proof exists with the given fingerprint.
     */
    boolean existsByFingerprint(String fingerprint);

    /** Constraint name for unique (mint_id, secret) - must match migration. */
    String CONSTRAINT_MINT_SECRET = "uk_proof_mint_secret";

    /** Constraint name for unique (mint_id, C) - must match migration. */
    String CONSTRAINT_MINT_COMMITMENT = "uk_proof_mint_commitment";

    /**
     * Tombstone a proof: sets {@code tombstoned_at = now()} and {@code tombstoned_by = principal}
     * if and only if the row exists for {@code (id, mintId)} and is not already tombstoned.
     * Returns the affected row count — 0 means either the row is missing or already tombstoned
     * (the caller decides which by a separate exists check).
     *
     * <p>FR-012: timestamp is sourced from the DB ({@code now()}), never the JVM clock.
     *
     * <p><b>Design note (post-G1, 2026-05-24):</b> The current production path in
     * {@code ProofVaultService.tombstone} uses a hybrid {@code save() → inline native UPDATE → refresh}
     * pattern instead of this method. The reason: this method's
     * {@code WHERE tombstoned_at IS NULL} guard rejects the clock-fix-up call that runs
     * <em>after</em> the JPA save has already populated {@code tombstoned_at}. The method is
     * retained as a documented escape hatch — a future strict-DB-clock-only path that bypasses
     * Envers entirely (e.g. a batch operator tool) can call it directly. The
     * {@code ProofRepositoryContractTest} continues to assert its presence as a regression net
     * against accidental deletion.
     */
    @Modifying
    @Query(value = """
            UPDATE t_proof
               SET tombstoned_at = now(),
                   tombstoned_by = :principal,
                   updated_at    = now()
             WHERE id = :id
               AND mint_id = :mintId
               AND tombstoned_at IS NULL
            """, nativeQuery = true)
    int tombstoneIfActive(@Param("id") UUID id,
                          @Param("mintId") UUID mintId,
                          @Param("principal") String principal);

    /**
     * State-only update for FR-008 — touches {@code state} and {@code updated_at}, nothing else.
     * Rows already tombstoned are NOT transitioned (callers receive 0 affected rows).
     *
     * <p><b>Design note (post-G1, 2026-05-24):</b> Same status as {@link #tombstoneIfActive} —
     * the production path uses a JPA save plus an inline native {@code updated_at = now()}
     * fix-up so that Envers fires on the state change. Method retained as a documented escape
     * hatch and as the regression net asserted by {@code ProofRepositoryContractTest}.
     */
    @Modifying
    @Query(value = """
            UPDATE t_proof
               SET state      = :newState,
                   updated_at = now()
             WHERE id = :id
               AND mint_id = :mintId
               AND tombstoned_at IS NULL
            """, nativeQuery = true)
    int updateState(@Param("id") UUID id,
                    @Param("mintId") UUID mintId,
                    @Param("newState") String newState);

    /**
     * Stores proof if not already present (duplicate detection).
     * Uses the unique constraint on (mint_id, secret) to prevent duplicates.
     *
     * NOTE: callers that need full FR-009 mismatch-rejection semantics MUST go through
     * {@code ProofVaultService.store(ProofEntity)}, which wraps this method with an
     * id-collision pre-check and a value-mismatch comparison on duplicate.
     */
    default InsertResult insertIfNotExists(ProofEntity proof) {
        UUID mintId = proof.getMint() != null ? proof.getMint().getId() : null;
        String secret = proof.getSecret();

        if (mintId == null) {
            throw new DataIntegrityViolationException("Proof must have a mint associated");
        }

        if (secret != null && existsByMint_IdAndSecret(mintId, secret)) {
            return new InsertResult(false, null, "Duplicate proof: same secret already exists for mint");
        }

        try {
            ProofEntity saved = save(proof);
            return new InsertResult(true, saved, null);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateConstraintViolation(e)) {
                return new InsertResult(false, null, "Duplicate proof detected by constraint: " + e.getMessage());
            }
            throw e;
        }
    }

    private static boolean isDuplicateConstraintViolation(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message == null) {
            Throwable cause = e.getCause();
            message = cause != null ? cause.getMessage() : "";
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains(CONSTRAINT_MINT_SECRET.toLowerCase())
                || lowerMessage.contains(CONSTRAINT_MINT_COMMITMENT.toLowerCase())
                || (lowerMessage.contains("unique") && lowerMessage.contains("secret"))
                || (lowerMessage.contains("unique") && lowerMessage.contains("mint_id"));
    }

    /** Result of an insert operation with duplicate detection. */
    record InsertResult(boolean stored, ProofEntity proof, String duplicateReason) {
        public boolean isDuplicate() {
            return !stored;
        }
    }

    // ----------------------------------------------------------------------
    // FR-001 — physical deletion is forbidden. Override every JpaRepository
    // mutator that removes rows to throw at runtime. The ProofRepositoryContractTest
    // (T013) asserts this lockdown.
    // ----------------------------------------------------------------------

    @Override
    default void delete(ProofEntity entity) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteById(UUID id) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAll(Iterable<? extends ProofEntity> entities) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAllInBatch() {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAllInBatch(Iterable<ProofEntity> entities) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) {
        throw new UnsupportedOperationException(
                "Use ProofVaultService.tombstone — physical deletion of proofs is forbidden");
    }
}
