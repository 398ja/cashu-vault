package xyz.tcheeric.cashu.vault.db.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import xyz.tcheeric.cashu.vault.db.dto.AuditTimelineDto;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneRequest;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneResponse;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.model.RevisionInfo;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer service for proof mutations governed by Constitution Principle I.
 *
 * The service is the SOLE entry point for tombstoning (FR-001, FR-002, FR-003),
 * state transitions (FR-008), and insert-with-mismatch-rejection (FR-009). Controllers
 * MUST delegate; repositories MUST NOT be invoked directly from controller code on these paths.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProofVaultService {

    private final ProofRepository proofRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // FR-009 — insert with mismatch rejection (the only safe insertion path)
    // -------------------------------------------------------------------------

    /**
     * Idempotent insertion: re-fetches on duplicate and verifies (mint, secret, c) match.
     * Three guards in order:
     *  1. id-collision pre-check (closes U1: blocks supplied-id+mutated-identity attacks)
     *  2. {@code insertIfNotExists}
     *  3. mismatch-on-duplicate (re-fetch + compare all three identity columns)
     *
     * <p>NOTE on concurrency: under truly simultaneous POSTs of the same {@code (mint_id, secret)}
     * one thread will commit the row and the other will get a unique-constraint violation at
     * transaction commit (after this method returns). The losing thread surfaces as a
     * generic data-integrity 409 to the caller. A stricter "always return 200 for the loser"
     * pattern would require {@code PROPAGATION_REQUIRES_NEW} around the insert + a fresh-tx
     * verify, but that breaks the legacy H2-backed integration tests which rely on a single
     * outer test transaction; that refactor is tracked for a follow-up spec alongside the
     * H2→Testcontainers migration of those tests (Constitution IV deferral D2). The
     * concurrency IT here accepts both outcomes and asserts the invariant that matters most:
     * exactly ONE row exists for the colliding {@code (mint_id, secret)} pair.
     */
    @Transactional
    public ProofEntity store(ProofEntity proposed) {
        UUID mintId = proposed.getMint() != null ? proposed.getMint().getId() : null;

        // Guard 1 — id-collision pre-check (FR-008 / FR-009 / U1).
        // ProofEntity.id defaults to UUID.randomUUID() (BaseEntity), so getId() is never null at
        // runtime — we check existsById on every call. A fresh client-side UUID collides only by
        // astronomical accident; a deliberate caller-supplied colliding id is the attack vector
        // this guard blocks.
        if (proofRepository.existsById(proposed.getId())) {
            logIntegrityMismatch("id_collision", mintId, proposed.getSecret());
            throw new DataIntegrityViolationException(
                    "identity-column conflict — caller supplied an id that already exists; "
                            + "updates of (mint_id, secret, c) via the insert path are forbidden");
        }

        // Guard 2 — insertIfNotExists (existing repo logic).
        ProofRepository.InsertResult result = proofRepository.insertIfNotExists(proposed);
        if (!result.isDuplicate()) {
            log.info("event=proof_store outcome=stored mintId={} secretPrefix={}",
                    mintId, prefix(proposed.getSecret()));
            return result.proof();
        }

        // Guard 3 — duplicate detected; verify it's a true idempotent match.
        ProofEntity existing = proofRepository
                .findByMint_IdAndSecret(mintId, proposed.getSecret())
                .orElseThrow(() -> new DataIntegrityViolationException(
                        "identity-column conflict — duplicate-detection found a row that "
                                + "disappeared on re-fetch (possible concurrent tombstone?)"));

        boolean mintMatch = existing.getMint() != null && existing.getMint().getId().equals(mintId);
        boolean secretMatch = java.util.Objects.equals(existing.getSecret(), proposed.getSecret());
        boolean cMatch = java.util.Objects.equals(existing.getUnblindedSignature(),
                proposed.getUnblindedSignature());

        if (mintMatch && secretMatch && cMatch) {
            log.info("event=proof_store outcome=duplicate_match mintId={} secretPrefix={}",
                    mintId, prefix(proposed.getSecret()));
            return existing;
        }

        logIntegrityMismatch("value_mismatch", mintId, proposed.getSecret());
        throw new DataIntegrityViolationException(
                "identity-column conflict — existing row's (mint_id, secret, c) does not match proposed insert");
    }

    // -------------------------------------------------------------------------
    // FR-008 — state transitions that touch only the state column
    // -------------------------------------------------------------------------

    @Transactional
    public ProofEntity markPending(UUID mintId, String secret, Authentication auth) {
        return transition(mintId, secret, ProofEntity.STATE_PENDING, auth);
    }

    @Transactional
    public ProofEntity markSpent(UUID mintId, String secret, Authentication auth) {
        return transition(mintId, secret, ProofEntity.STATE_SPENT, auth);
    }

    private ProofEntity transition(UUID mintId, String secret, String newState, Authentication auth) {
        ProofEntity existing = proofRepository.findByMint_IdAndSecret(mintId, secret)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no proof for (mintId, secret)"));

        if (existing.getTombstonedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_TOMBSTONED");
        }

        String prev = existing.getState();
        if (!isLegalTransition(prev, newState)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "illegal state transition " + prev + " -> " + newState);
        }

        // FR-010 — JPA-managed setter+save triggers Envers (a native UPDATE would not).
        // FR-008 — identity columns are updatable=false on the entity, so the generated UPDATE
        //          touches only state-machine columns (state + updated_at).
        existing.setState(newState);
        proofRepository.save(existing);
        entityManager.flush();
        // FR-012 (strict on live row) — overwrite updated_at with DB clock. The audit row keeps
        // the JPA-managed (JVM) value at flush time — acceptable per the FR-012 wording.
        int affected = entityManager.createNativeQuery(
                        "UPDATE t_proof SET updated_at = now() WHERE id = :id")
                .setParameter("id", existing.getId())
                .executeUpdate();
        if (affected != 1) {
            throw new IllegalStateException("expected 1 row updated by state-transition clock fix-up, got " + affected);
        }
        entityManager.refresh(existing);

        log.info("event=proof_state_transition outcome=state_transition mintId={} secretPrefix={} from={} to={} principal={}",
                mintId, prefix(secret), prev, newState, principalName(auth));

        return existing;
    }

    private static boolean isLegalTransition(String from, String to) {
        if (from == null) {
            return false;
        }
        // Legal: UNSPENT -> PENDING -> SPENT, UNSPENT -> SPENT.
        // Re-asserting the same state is treated as legal (idempotent client retries).
        if (from.equals(to)) {
            return true;
        }
        if (ProofEntity.STATE_UNSPENT.equals(from)) {
            return ProofEntity.STATE_PENDING.equals(to) || ProofEntity.STATE_SPENT.equals(to);
        }
        if (ProofEntity.STATE_PENDING.equals(from)) {
            return ProofEntity.STATE_SPENT.equals(to);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // FR-002, FR-003, FR-010, FR-012 — admin tombstone
    // -------------------------------------------------------------------------

    @Transactional
    public TombstoneResponse tombstone(UUID mintId, String secret, TombstoneRequest req, Authentication auth) {
        ProofEntity existing = proofRepository.findByMint_IdAndSecret(mintId, secret)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no proof for (mintId, secret)"));

        if (existing.getTombstonedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_TOMBSTONED");
        }

        boolean unspent = ProofEntity.STATE_UNSPENT.equals(existing.getState());
        if (unspent && !req.force()) {
            log.warn("event=proof_tombstone outcome=tombstone_rejected_unspent mintId={} secretPrefix={} principal={}",
                    mintId, prefix(secret), principalName(auth));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOMBSTONE_REQUIRES_FORCE");
        }

        String principal = principalName(auth);

        // FR-012 — strict on the LIVE row, approximate on the audit row.
        // Step 1: JPA setter+save fires Envers (so t_proof_a gets a tombstone revision) with a
        //         JVM-clock placeholder. Required because native UPDATEs bypass Envers entirely.
        existing.setTombstonedAt(Instant.now());
        existing.setTombstonedBy(principal);
        proofRepository.save(existing);
        entityManager.flush();
        // Step 2: overwrite the live row's tombstoned_at with the DB clock (FR-012 strict for
        //         the canonical state-of-truth). The audit row's value is now sub-second behind
        //         but the live row exposed to operators and queries is DB-true.
        int affected = entityManager.createNativeQuery(
                        "UPDATE t_proof SET tombstoned_at = now() WHERE id = :id")
                .setParameter("id", existing.getId())
                .executeUpdate();
        if (affected != 1) {
            throw new IllegalStateException("expected 1 row updated by tombstone clock fix-up, got " + affected);
        }
        // Step 3: refresh the managed entity so the response carries the DB-clock value.
        entityManager.refresh(existing);
        ProofEntity reloaded = existing;

        String severity = req.force() ? "high" : "normal";
        log.warn("event=proof_tombstone outcome=tombstoned mintId={} secretPrefix={} principal={} priorState={} force={} severity={} reasonLen={}",
                mintId, prefix(secret), principal, existing.getState(), req.force(), severity,
                req.reason() != null ? req.reason().length() : 0);

        return new TombstoneResponse(mintId, secret, reloaded.getTombstonedAt(), reloaded.getTombstonedBy());
    }

    // -------------------------------------------------------------------------
    // FR-013 — admin audit timeline
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuditTimelineDto getAuditTimeline(UUID mintId, String secret) {
        ProofEntity current = proofRepository.findByMint_IdAndSecret(mintId, secret)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no proof for (mintId, secret)"));

        AuditReader reader = AuditReaderFactory.get(entityManager);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(ProofEntity.class, false, true)
                .add(AuditEntity.id().eq(current.getId()))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<AuditTimelineDto.Revision> revisions = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ProofEntity rev = (ProofEntity) row[0];
            RevisionInfo info = (RevisionInfo) row[1];
            RevisionType type = (RevisionType) row[2];
            revisions.add(new AuditTimelineDto.Revision(
                    info.getRev(),
                    info.getRevtstmp() != null ? Instant.ofEpochMilli(info.getRevtstmp()) : null,
                    info.getPrincipalId(),
                    type.name(),
                    rev != null ? rev.getState() : null,
                    rev != null ? rev.getTombstonedAt() : null
            ));
        }

        return new AuditTimelineDto(
                mintId,
                prefix(secret),
                new AuditTimelineDto.Current(current.getState(), current.getTombstonedAt(), current.getTombstonedBy()),
                revisions
        );
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void logIntegrityMismatch(String reason, UUID mintId, String secret) {
        log.warn("event=proof_insert_mismatch outcome=integrity_mismatch reason={} severity=high mintId={} secretPrefix={}",
                reason, mintId, prefix(secret));
    }

    private static String prefix(String secret) {
        if (secret == null) {
            return null;
        }
        return secret.length() > 6 ? secret.substring(0, 6) : secret;
    }

    private static String principalName(Authentication auth) {
        return auth != null ? auth.getName() : "anonymous";
    }
}
