package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.dto.AuditTimelineDto;
import xyz.tcheeric.cashu.vault.db.dto.ErrorEnvelope;
import xyz.tcheeric.cashu.vault.db.dto.StateTransitionRequest;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneRequest;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneResponse;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;
import xyz.tcheeric.cashu.vault.db.service.ProofVaultService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller exposing CRUD-style endpoints for {@link ProofEntity} resources.
 *
 * Spec 001 changes:
 *  - DELETE /vault/proof/{id} REMOVED (FR-001); tombstone is the replacement (FR-002).
 *  - GET /vault/proof/secret/{secret} is a 400 stub returning a deprecation envelope (FR-005).
 *  - Every mint-scoped read/write cross-checks the requested mintId against the principal's
 *    {@code MINT:<uuid>} GrantedAuthority unless the caller is {@code ROLE_ADMIN} (FR-006).
 *  - POST /vault/proof goes through {@link ProofVaultService#store(ProofEntity)} for
 *    insertIfNotExists + identity-column mismatch rejection (FR-009).
 *  - POST /vault/proof/mint/{mintId}/secret/{secret}/state for state-only transitions (FR-008).
 *  - POST /vault/proof/mint/{mintId}/secret/{secret}/tombstone for admin tombstone (FR-002, FR-003).
 *  - GET  /vault/proof/mint/{mintId}/secret/{secret}/audit for the admin audit timeline (FR-013).
 */
@RestController
@RequestMapping("/vault/proof")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ProofVaultController {

    private final ProofRepository proofRepository;
    private final MintRepository mintRepository;
    private final ProofVaultService proofVaultService;

    /**
     * Stores a new proof entity with duplicate detection and identity-column mismatch rejection.
     */
    @PostMapping
    public ResponseEntity<ProofEntity> store(@RequestBody ProofEntity proof, Authentication auth) throws CashuErrorException {
        log.info("Storing ProofEntity");

        if (proof.getMint() != null && proof.getMint().getId() != null) {
            assertMintScope(proof.getMint().getId(), auth);
            Optional<MintEntity> mintOpt = mintRepository.findById(proof.getMint().getId());
            if (mintOpt.isPresent()) {
                proof.setMint(mintOpt.get());
            } else {
                log.warn("Mint not found for ID: {}", proof.getMint().getId());
                return ResponseEntity.badRequest().build();
            }
        }

        // FR-009 — delegate to service so id-collision + mismatch guards are enforced.
        ProofEntity stored = proofVaultService.store(proof);
        log.debug("Stored ProofEntity {}", stored.getId());
        return ResponseEntity.ok(stored);
    }

    /**
     * Lists all proofs — admin-only operational scan.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProofEntity>> list() {
        return ResponseEntity.ok(proofRepository.findAll());
    }

    /**
     * Retrieves a proof by its identifier. Response is mint-scope-checked against the principal.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProofEntity> retrieve(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id,
            Authentication auth) throws CashuErrorException {
        log.info("Retrieving ProofEntity by id {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            ProofEntity p = proofOpt.get();
            if (p.getMint() != null && p.getMint().getId() != null) {
                assertMintScope(p.getMint().getId(), auth);
            }
            log.debug("Retrieved ProofEntity by id {}", p.getId());
            return ResponseEntity.ok(p);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<ProofEntity>> retrieveByMint(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            Authentication auth) throws CashuErrorException {
        log.info("Retrieving ProofEntities by mintId {}", mintId);
        UUID mintUuid = UUID.fromString(mintId);
        assertMintScope(mintUuid, auth);
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_Id(mintUuid);
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        throw new CashuErrorException("No ProofEntities found for the given Mint ID");
    }

    /**
     * FR-005 — legacy global-secret endpoint. Returns 400 with a deprecation envelope and DOES NOT
     * access the database. Removed entirely in v0.8.0.
     */
    @GetMapping("/secret/{secret}")
    public ResponseEntity<ErrorEnvelope> retrieveBySecret(
            @PathVariable("secret") @NotBlank @Size(max = 512, message = "Secret exceeds maximum length") String secret) {
        log.warn("event=legacy_endpoint_called path=/vault/proof/secret/* secretPrefix={}",
                secret.length() > 6 ? secret.substring(0, 6) : secret);
        return ResponseEntity.badRequest().body(new ErrorEnvelope(
                "MINT_SCOPE_REQUIRED",
                "Use GET /vault/proof/mint/{mintId}/secret/{secret}. This endpoint is removed in v0.8.0.",
                Map.of("since", "0.7.0", "removalTarget", "0.8.0")));
    }

    @GetMapping("/mint/{mintId}/secret/{secret}")
    public ResponseEntity<ProofEntity> retrieveByMintAndSecret(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("secret") @NotBlank @Size(max = 512, message = "Secret exceeds maximum length") String secret,
            Authentication auth) throws CashuErrorException {
        log.info("Retrieving ProofEntity by mintId {} and secret {}", mintId, secret);
        UUID mintUuid = UUID.fromString(mintId);
        assertMintScope(mintUuid, auth);
        Optional<ProofEntity> proof = proofRepository.findByMint_IdAndSecret(mintUuid, secret);
        if (proof.isPresent()) {
            return ResponseEntity.ok(proof.get());
        }
        log.warn("No ProofEntity found for the specified mint and secret");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mint/{mintId}/amount/{amount}")
    public ResponseEntity<Set<ProofEntity>> retrieveByMintAndAmount(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("amount") @Positive(message = "Amount must be positive") Integer amount,
            Authentication auth) throws CashuErrorException {
        log.info("Retrieving ProofEntities by mintId {} and amount {}", mintId, amount);
        UUID mintUuid = UUID.fromString(mintId);
        assertMintScope(mintUuid, auth);
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_IdAndAmount(mintUuid, amount);
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mint/{mintId}/signature/{unblindedSignature}")
    public ResponseEntity<ProofEntity> retrieveByMintAndUnblindedSignature(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("unblindedSignature") @NotBlank @Size(max = 512, message = "Signature exceeds maximum length") String unblindedSignature,
            Authentication auth) throws CashuErrorException {
        log.info("Retrieving ProofEntity by mintId {} and signature {}", mintId, unblindedSignature);
        UUID mintUuid = UUID.fromString(mintId);
        assertMintScope(mintUuid, auth);
        Optional<ProofEntity> proof = proofRepository.findByMint_IdAndUnblindedSignature(mintUuid, unblindedSignature);
        if (proof.isPresent()) {
            return ResponseEntity.ok(proof.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * FR-008 — state-only transition (PENDING / SPENT). Re-saving the whole entity via POST /vault/proof
     * is no longer how state changes happen.
     */
    @PostMapping("/mint/{mintId}/secret/{secret}/state")
    public ResponseEntity<ProofEntity> transitionState(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("secret") @NotBlank @Size(max = 512, message = "Secret exceeds maximum length") String secret,
            @Valid @RequestBody StateTransitionRequest body,
            Authentication auth) {
        log.info("Transitioning ProofEntity state mintId={} to={}", mintId, body.to());
        UUID mintUuid = UUID.fromString(mintId);
        assertMintScope(mintUuid, auth);
        ProofEntity updated = ProofEntity.STATE_PENDING.equals(body.to())
                ? proofVaultService.markPending(mintUuid, secret, auth)
                : proofVaultService.markSpent(mintUuid, secret, auth);
        return ResponseEntity.ok(updated);
    }

    /**
     * FR-002, FR-003 — admin-only tombstone. Replaces the removed DELETE /{id} endpoint.
     */
    @PostMapping("/mint/{mintId}/secret/{secret}/tombstone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TombstoneResponse> tombstone(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("secret") @NotBlank @Size(max = 512, message = "Secret exceeds maximum length") String secret,
            @Valid @RequestBody TombstoneRequest body,
            Authentication auth) {
        log.info("Tombstoning ProofEntity mintId={} force={}", mintId, body.force());
        UUID mintUuid = UUID.fromString(mintId);
        TombstoneResponse resp = proofVaultService.tombstone(mintUuid, secret, body, auth);
        return ResponseEntity.ok(resp);
    }

    /**
     * FR-013 — admin-only audit timeline.
     */
    @GetMapping("/mint/{mintId}/secret/{secret}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditTimelineDto> audit(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("secret") @NotBlank @Size(max = 512, message = "Secret exceeds maximum length") String secret) {
        log.info("Audit timeline mintId={} secretPrefix={}", mintId,
                secret.length() > 6 ? secret.substring(0, 6) : secret);
        return ResponseEntity.ok(proofVaultService.getAuditTimeline(UUID.fromString(mintId), secret));
    }

    /**
     * Archives a proof by marking it as archived. (Distinct from tombstone.)
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<ProofEntity> archive(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id,
            Authentication auth) throws CashuErrorException {
        log.info("Archiving ProofEntity {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            ProofEntity proof = proofOpt.get();
            if (proof.getMint() != null && proof.getMint().getId() != null) {
                assertMintScope(proof.getMint().getId(), auth);
            }
            proof.setArchived(true);
            ProofEntity archivedProof = proofRepository.save(proof);
            log.debug("Archived ProofEntity {}", archivedProof.getId());
            return ResponseEntity.ok(archivedProof);
        }
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // FR-006 — mint-scope cross-check helper.
    // Admin accounts may operate across any mint.
    // Service accounts MUST carry a MINT:<uuid> GrantedAuthority matching the requested mintId.
    // -------------------------------------------------------------------------
    private static void assertMintScope(UUID requestedMintId, Authentication auth) {
        if (auth == null) {
            // Security disabled (test profile) — skip.
            return;
        }
        boolean admin = false;
        boolean scoped = false;
        String target = "MINT:" + requestedMintId;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority();
            if ("ROLE_ADMIN".equals(s)) {
                admin = true;
            } else if (target.equals(s)) {
                scoped = true;
            }
        }
        if (!admin && !scoped) {
            throw new AccessDeniedException("mint-scope mismatch — principal not authorized for the requested mint");
        }
    }
}
