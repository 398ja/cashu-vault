package xyz.tcheeric.cashu.vault.db.repos;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.HoldKind;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

/** Repository interface for {@link ProofEntity} storage operations. */
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
   * @param id mint identifier
   * @param secret proof secret
   * @return optional proof entity
   */
  Optional<ProofEntity> findByMint_IdAndSecret(UUID id, String secret);

  /**
   * Finds proofs by the mint they belong to and their amount.
   *
   * @param id mint identifier
   * @param amount proof amount
   * @return optional set of proof entities
   */
  Optional<Set<ProofEntity>> findByMint_IdAndAmount(UUID id, Integer amount);

  /**
   * Finds a proof by the mint it belongs to and its unblinded signature.
   *
   * @param id mint identifier
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
   * @param mintId mint identifier
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

  /** Constraint name for unique (mint_id, secret) - must match migration. */
  String CONSTRAINT_MINT_SECRET = "uk_proof_mint_secret";

  /** Constraint name for unique (mint_id, C) - must match migration. */
  String CONSTRAINT_MINT_COMMITMENT = "uk_proof_mint_commitment";

  /**
   * Stores a proof as a new row, never as an update of an existing one.
   *
   * <p>The row is rebuilt from the caller's proof fields, so nothing the caller sends can make this
   * an update. It used to {@code save()} the caller's entity as-is, and because {@code ProofEntity}
   * is versioned, {@code save()} of an entity carrying an existing id and a non-null version is a
   * JPA merge: a body naming an existing row's id overwrote that row. That is how the mint marked
   * proofs spent (load, set SPENT, re-POST), and it is also how anyone holding the token could turn
   * a SPENT row back into an UNSPENT one (cashu-vault#154).
   *
   * <p>A caller-supplied id is kept, so a client that reads back its own id still can, but an id
   * that already exists is a duplicate rather than a target. The version is cleared so the
   * repository persists instead of merging, which makes the primary key the last guard.
   *
   * @param proof proof to store
   * @return InsertResult indicating success or duplicate
   * @throws DataIntegrityViolationException for non-duplicate constraint violations (e.g., NOT
   *     NULL, FK violations)
   */
  default InsertResult insertIfNotExists(ProofEntity proof) {
    UUID mintId = proof.getMint() != null ? proof.getMint().getId() : null;

    // Validate required fields - throw early for null mint (NOT NULL constraint)
    if (mintId == null) {
      throw new DataIntegrityViolationException("Proof must have a mint associated");
    }

    String duplicate = duplicateReason(proof);
    return duplicate != null ? new InsertResult(false, null, duplicate) : insertNew(proof);
  }

  /**
   * Why {@code proof} would duplicate a stored row, or {@code null} if it would not: an existing id
   * (which {@code save()} would merge over, cashu-vault#154) or an existing secret for the mint.
   * Read-only, so a caller can run it in the same transaction as its other lookups.
   */
  default String duplicateReason(ProofEntity proof) {
    if (proof.getId() != null && existsById(proof.getId())) {
      return "Duplicate proof: id already exists";
    }
    String secret = proof.getSecret();
    if (secret != null && existsByMint_IdAndSecret(proof.getMint().getId(), secret)) {
      return "Duplicate proof: same secret already exists for mint";
    }
    return null;
  }

  /**
   * Inserts a proof already checked by {@link #duplicateReason}. A racing insert of the same proof
   * is still caught here, by the unique constraints, and reported as a duplicate.
   */
  default InsertResult insertNew(ProofEntity proof) {
    try {
      ProofEntity saved = save(buildInsertRow(proof));
      return new InsertResult(true, saved, null);
    } catch (DataIntegrityViolationException e) {
      // Only treat as duplicate if it's a unique constraint violation on our duplicate-detection
      // constraints
      if (isDuplicateConstraintViolation(e)) {
        return new InsertResult(
            false, null, "Duplicate proof detected by constraint: " + e.getMessage());
      }
      // Rethrow other integrity violations (NOT NULL, FK, etc.)
      throw e;
    }
  }

  /**
   * Builds a fresh row carrying only the fields a caller may set on a new proof.
   *
   * <p>State is copied because {@code storePending} legitimately inserts a PENDING row; the
   * controller has already refused any state other than UNSPENT or PENDING. Holds, the archived
   * flag, timestamps and the version stay server-managed.
   */
  private static ProofEntity buildInsertRow(ProofEntity src) {
    ProofEntity row = new ProofEntity();
    if (src.getId() != null) {
      row.setId(src.getId());
    }
    row.setVersion(null);
    row.setMint(src.getMint());
    row.setAmount(src.getAmount());
    row.setSecret(src.getSecret());
    row.setUnblindedSignature(src.getUnblindedSignature());
    row.setWitness(src.getWitness());
    row.setFingerprint(src.getFingerprint());
    row.setKeysetId(src.getKeysetId());
    row.setState(src.getState() == null ? ProofEntity.STATE_UNSPENT : src.getState());
    return row;
  }

  /**
   * Checks if the exception is a unique constraint violation for duplicate detection. Only returns
   * true for violations of uk_proof_mint_secret or uk_proof_mint_commitment.
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
        || lowerMessage.contains("pk_t_proof")
        // Also check for generic unique violation patterns that mention our columns
        || (lowerMessage.contains("unique") && lowerMessage.contains("secret"))
        || (lowerMessage.contains("unique") && lowerMessage.contains("mint_id"));
  }

  /** Result of an insert operation with duplicate detection. */
  record InsertResult(boolean stored, ProofEntity proof, String duplicateReason) {
    public boolean isDuplicate() {
      return !stored;
    }
  }

  // ---------------------------------------------------------------
  // cashu-mint spec 002 T011 — melt saga binding helpers
  // ---------------------------------------------------------------

  /**
   * Spec 002 T011 — atomically marks the named proofs PENDING and binds them to a single melt saga.
   * Exclusivity is enforced by the UPDATE predicate itself ({@code state='UNSPENT' AND hold_id IS
   * NULL}): a concurrent claim on the same row sees rowcount=0 (no exception); the losing caller
   * bails out by comparing the returned count against {@code proofIds.size()}.
   *
   * <p>Returns the number of rows updated. A return value less than {@code proofIds.size()} means
   * partial application — some proofs already SPENT, already held by another saga, or missing.
   *
   * @param proofIds Y-coordinate secrets of the proofs to claim
   * @param holdId saga id that will hold the proofs
   * @param mintId mint that owns the proofs (scopes the update)
   * @return number of rows actually transitioned UNSPENT → PENDING
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE proof p SET p.state = 'PENDING', p.holdId = :holdId "
          + "WHERE p.mint.id = :mintId "
          + "AND p.secret IN :proofIds "
          + "AND p.state = 'UNSPENT' "
          + "AND p.holdId IS NULL")
  int markPending(
      @Param("proofIds") Collection<String> proofIds,
      @Param("holdId") String holdId,
      @Param("mintId") UUID mintId);

  /**
   * Spec 002 T011 — clears {@code hold_id} on the named proofs. Called when a saga transitions out
   * of PROOFS_HELD: either to COMPLETED (also flips state to SPENT — see {@link #commitSpent}), to
   * FAILED (rolls back to UNSPENT — see {@link #refundToUnspent}), or operator-initiated cleanup.
   *
   * @return number of rows actually cleared
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("UPDATE proof p SET p.holdId = NULL " + "WHERE p.holdId = :holdId")
  int clearHold(@Param("holdId") String holdId);

  /**
   * Spec 002 T011 — commits a saga's PENDING proofs as SPENT and clears the saga binding
   * atomically. Used by {@code MeltTask} on the happy-path transition {@code PAYMENT_SENT →
   * COMPLETED}.
   *
   * @return number of rows actually transitioned PENDING → SPENT
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE proof p SET p.state = 'SPENT', p.holdId = NULL, p.spentAt = INSTANT "
          + "WHERE p.holdId = :holdId "
          + "AND p.state = 'PENDING'")
  int commitSpent(@Param("holdId") String holdId);

  /**
   * Spec 002 T011 — refunds a saga's PENDING proofs back to UNSPENT and clears the saga binding
   * atomically. Used by {@code MeltTask} on the compensation transition {@code PROOFS_HELD →
   * FAILED} (and by the scheduled reconciler's PROOFS_HELD TTL sweep).
   *
   * @return number of rows actually transitioned PENDING → UNSPENT
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE proof p SET p.state = 'UNSPENT', p.holdId = NULL "
          + "WHERE p.holdId = :holdId "
          + "AND p.state = 'PENDING'")
  int refundToUnspent(@Param("holdId") String holdId);

  /**
   * cashu-vault#154 — the narrow SPENT transition that replaces re-posting a whole entity.
   *
   * <p>Moves the named proofs of one mint to SPENT from UNSPENT or PENDING, and clears any hold on
   * them, whichever flow holds them: a proof whose spend has already happened is spent regardless
   * of who else was about to spend it. A row already SPENT is left untouched, so a retry is
   * harmless. Nothing here can move a row out of SPENT.
   *
   * @param secrets Y-normalised secrets of the proofs to mark
   * @param mintId mint that owns the proofs
   * @return number of rows this call transitioned
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "UPDATE proof p SET p.state = 'SPENT', p.holdId = NULL, p.holdKind = NULL, "
          + "p.spentAt = INSTANT "
          + "WHERE p.mint.id = :mintId "
          + "AND p.secret IN :secrets "
          + "AND p.state IN ('UNSPENT', 'PENDING')")
  int markSpent(@Param("secrets") Collection<String> secrets, @Param("mintId") UUID mintId);

  /**
   * Counts how many of the named proofs of one mint are SPENT. Paired with {@link #markSpent} so
   * the caller learns whether every proof it spent is now recorded as spent, including the ones an
   * earlier attempt already marked.
   */
  @Query(
      "SELECT COUNT(p) FROM proof p WHERE p.mint.id = :mintId "
          + "AND p.secret IN :secrets AND p.state = 'SPENT'")
  long countSpent(@Param("secrets") Collection<String> secrets, @Param("mintId") UUID mintId);

  /**
   * Spec 002 T011 — operator-visible enumeration of proofs currently held by a saga. Backs the
   * admin saga query endpoint + the daily SC-002 reconciliation invariant ({@code every COMPLETED
   * saga has 0 still-held proofs}).
   */
  @Query("SELECT p FROM proof p WHERE p.holdId = :holdId")
  List<ProofEntity> findByMeltSagaId(@Param("holdId") String holdId);

  /**
   * Spec 005 — atomic insert-or-claim used by the melt saga to durably hold a proof before any
   * external Lightning payment is attempted.
   *
   * <p>Replaces the prior two-step "insert as PENDING, then UPDATE UNSPENT → PENDING" sequence,
   * which could never claim a freshly inserted PENDING row and would therefore leave {@code bound =
   * 0} for the first-time melt of a proof — letting the saga proceed to {@code
   * lightningPaymentPort.pay} with no durable hold.
   *
   * <p>Behaviour, per proof:
   *
   * <ol>
   *   <li>UPDATE existing row to PENDING + this saga id, gated on {@code state='UNSPENT' AND
   *       hold_id IS NULL}. Matches an existing UNSPENT row owned by no saga.
   *   <li>If the UPDATE matched zero rows AND no row exists for {@code (mint_id, secret)}: INSERT a
   *       fresh row in state {@code PENDING} with {@code hold_id = sagaId}.
   *   <li>If the INSERT loses a race to the uniqueness constraint {@code uk_proof_mint_secret}:
   *       re-attempt the UPDATE. If the racing row is UNSPENT we claim it; if it is PENDING/SPENT
   *       we lose and return 0 for this proof.
   * </ol>
   *
   * <p>The caller MUST submit Y-coordinate-normalised secrets (see {@link ProofEntity#fromProof}).
   * Mixing raw secrets and Y values here would let two rows refer to the same logical proof.
   *
   * <p>Returns the total number of proofs durably bound to {@code holdId} after this call. Caller
   * compares the returned count to {@code proofs.size()} and aborts (and releases any partial holds
   * via {@code refundToUnspent}) on mismatch.
   *
   * <p>The DB unique constraint {@code uk_proof_mint_secret} is the race guard. The retry-once
   * pattern is sufficient: the racing transaction has committed by the time we see {@link
   * DataIntegrityViolationException}, so the retry sees a settled row.
   *
   * @param proofs proofs to claim, already Y-normalised
   * @param holdId saga id to bind successfully claimed rows to
   * @param mintId mint scope for the claim
   * @return number of proofs actually bound to {@code holdId}
   */
  @Transactional
  default int insertOrClaimForHold(
      @org.springframework.lang.NonNull List<ProofEntity> proofs,
      @org.springframework.lang.NonNull String holdId,
      @org.springframework.lang.NonNull UUID mintId) {
    // One CAS claims every UNSPENT row at once. After it, a row bound to this hold is bound
    // whether this call or an earlier retry claimed it, so one read settles every existing
    // secret and only the absent ones need an insert.
    List<String> secrets = proofs.stream().map(ProofEntity::getSecret).distinct().toList();
    markPending(secrets, holdId, mintId);
    Map<String, ProofEntity> existing =
        findByMint_IdAndSecretIn(mintId, secrets).stream()
            .collect(Collectors.toMap(ProofEntity::getSecret, Function.identity()));
    int bound = 0;
    Map<String, Boolean> inserted = new HashMap<>();
    for (ProofEntity proof : proofs) {
      String secret = proof.getSecret();
      boolean claimed =
          existing.containsKey(secret)
              ? isHeldBy(existing.get(secret), holdId)
              : inserted.computeIfAbsent(secret, s -> insertHoldRow(proof, holdId, mintId));
      if (claimed) {
        bound++;
      }
    }
    return bound;
  }

  /**
   * Finds the proofs of a mint with any of the given secrets.
   *
   * @param mintId mint identifier
   * @param secrets proof secrets
   * @return the matching proofs
   */
  List<ProofEntity> findByMint_IdAndSecretIn(UUID mintId, Collection<String> secrets);

  private static boolean isHeldBy(ProofEntity row, String holdId) {
    return ProofEntity.STATE_PENDING.equals(row.getState()) && holdId.equals(row.getHoldId());
  }

  /**
   * Inserts a fresh PENDING row built from scratch, so caller-supplied identity fields can never
   * turn save() into a merge. Losing the (mint_id, secret) race retries the claim once: the racer
   * has committed, so its row is settled. Any other integrity error is a real fault and propagates.
   */
  private boolean insertHoldRow(ProofEntity proof, String holdId, UUID mintId) {
    try {
      save(buildHoldRow(proof, holdId));
      return true;
    } catch (DataIntegrityViolationException e) {
      if (!isMintSecretUniqueViolation(e)) {
        throw e;
      }
      return markPending(List.of(proof.getSecret()), holdId, mintId) > 0
          || heldByThisHold(mintId, proof.getSecret(), holdId);
    }
  }

  /**
   * True iff a row exists for {@code (mintId, secret)} that is currently {@code PENDING} and bound
   * to {@code holdId} — the idempotent re-claim case for a single saga.
   */
  private boolean heldByThisHold(UUID mintId, String secret, String holdId) {
    return findByMint_IdAndSecret(mintId, secret)
        .map(row ->
            ProofEntity.STATE_PENDING.equals(row.getState()) && holdId.equals(row.getHoldId()))
        .orElse(false);
  }

  /**
   * Builds a fresh PENDING hold row for the insert path, copying only the caller-meaningful proof
   * fields. Server-managed identity / audit fields (id, version, archived, timestamps) keep their
   * {@code BaseEntity} defaults so {@code save()} performs an INSERT, never a merge. The version is
   * cleared because Spring Data treats a versioned entity as new only while its version is null; at
   * the default 0 it would merge, paying a SELECT before every INSERT.
   */
  private static ProofEntity buildHoldRow(ProofEntity src, String holdId) {
    ProofEntity row = new ProofEntity();
    row.setVersion(null);
    row.setMint(src.getMint());
    row.setAmount(src.getAmount());
    row.setSecret(src.getSecret());
    row.setUnblindedSignature(src.getUnblindedSignature());
    row.setWitness(src.getWitness());
    row.setKeysetId(src.getKeysetId());
    row.setState(ProofEntity.STATE_PENDING);
    row.setHoldId(holdId);
    row.setHoldKind(HoldKind.forHoldId(holdId));
    return row;
  }

  /**
   * Narrow check: is this integrity violation specifically the {@code uk_proof_mint_secret}
   * uniqueness constraint? Mirrors the matching in {@link #insertIfNotExists} but scoped to the
   * secret constraint only (the commitment constraint and NOT NULL / FK violations must propagate,
   * not be swallowed as a claim race).
   */
  private static boolean isMintSecretUniqueViolation(DataIntegrityViolationException e) {
    String message = e.getMessage();
    if (message == null) {
      Throwable cause = e.getCause();
      message = cause != null ? cause.getMessage() : "";
    }
    String lower = message == null ? "" : message.toLowerCase();
    return lower.contains(CONSTRAINT_MINT_SECRET.toLowerCase())
        || (lower.contains("unique") && lower.contains("secret"));
  }
}
