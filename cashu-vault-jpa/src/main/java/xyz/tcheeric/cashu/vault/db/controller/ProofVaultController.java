package xyz.tcheeric.cashu.vault.db.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller exposing CRUD-style endpoints for {@link ProofEntity}
 * resources.
 */
@RestController
@RequestMapping("/vault/proof")
@RequiredArgsConstructor
@Slf4j
public class ProofVaultController {

    private final ProofRepository proofRepository;
    private final MintRepository mintRepository;

    /**
     * Stores a new proof entity with duplicate detection.
     * Returns 409 Conflict if the proof already exists.
     *
     * @param proof proof to persist
     * @return stored proof entity, or 409 if duplicate
     * @throws CashuErrorException in case the entity cannot be persisted
     */
    @PostMapping
    public ResponseEntity<ProofEntity> store(@RequestBody ProofEntity proof) throws CashuErrorException {
        log.info("Storing ProofEntity with secret: {}...",
                proof.getSecret() != null ? proof.getSecret().substring(0, Math.min(16, proof.getSecret().length())) : "null");

        // Look up mint to get managed entity (avoid cascade issues with detached entity)
        if (proof.getMint() != null && proof.getMint().getId() != null) {
            Optional<MintEntity> mintOpt = mintRepository.findById(proof.getMint().getId());
            if (mintOpt.isPresent()) {
                proof.setMint(mintOpt.get());
            } else {
                log.warn("Mint not found for ID: {}", proof.getMint().getId());
                return ResponseEntity.badRequest().build();
            }
        }

        var result = proofRepository.insertIfNotExists(proof);

        if (result.isDuplicate()) {
            log.info("Duplicate proof detected: {}", result.duplicateReason());
            return ResponseEntity.status(409).build();
        }

        log.debug("Stored ProofEntity {}", result.proof().getId());
        return ResponseEntity.ok(result.proof());
    }

    /**
     * Retrieves all proof entities.
     *
     * @return list of proofs (possibly empty)
     */
    @GetMapping
    public ResponseEntity<List<ProofEntity>> list() {
        return ResponseEntity.ok(proofRepository.findAll());
    }

    /**
     * Retrieves a proof by its identifier.
     *
     * @param id proof identifier
     * @return matching proof entity
     * @throws CashuErrorException if no proof with the ID exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProofEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving ProofEntity by id {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            log.debug("Retrieved ProofEntity by id {}", proofOpt.get().getId());
            return ResponseEntity.ok(proofOpt.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves all proofs associated with a mint.
     *
     * @param mintId mint identifier
     * @return set of proofs for the mint
     * @throws CashuErrorException if none are found
     */
    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<ProofEntity>> retrieveByMint(@PathVariable("mintId") String mintId) throws CashuErrorException {
        log.info("Retrieving ProofEntities by mintId {}", mintId);
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_Id(UUID.fromString(mintId));
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        throw new CashuErrorException("No ProofEntities found for the given Mint ID");
    }

    /**
     * Retrieves a proof by its secret.
     *
     * @param secret secret value
     * @return matching proof entity
     * @throws CashuErrorException if no proof exists for the secret
     */
    @GetMapping("/secret/{secret}")
    public ResponseEntity<ProofEntity> retrieveBySecret(@PathVariable("secret") String secret) throws CashuErrorException {
        log.debug("Retrieving ProofEntity by secret {}", secret);
        Optional<ProofEntity> proof = proofRepository.findBySecret(secret);
        if (proof.isPresent()) {
            log.debug("Retrieved ProofEntity by secret {}", secret);
            return ResponseEntity.ok(proof.get());
        }
        log.warn("No ProofEntity found for the specified secret");
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves a proof by mint and secret.
     *
     * @param mintId mint identifier
     * @param secret secret value
     * @return matching proof entity
     * @throws CashuErrorException if no proof exists for the mint/secret pair
     */
    @GetMapping("/mint/{mintId}/secret/{secret}")
    public ResponseEntity<ProofEntity> retrieveByMintAndSecret(@PathVariable("mintId") String mintId,
            @PathVariable("secret") String secret) throws CashuErrorException {
        log.info("Retrieving ProofEntity by mintId {} and secret {}", mintId, secret);
        Optional<ProofEntity> proof = proofRepository.findByMint_IdAndSecret(UUID.fromString(mintId), secret);
        if (proof.isPresent()) {
            log.debug("Retrieved ProofEntity by mintId {} and secret {}", mintId, secret);
            return ResponseEntity.ok(proof.get());
        }
        log.warn("No ProofEntity found for the specified mint and secret");
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves proofs by mint and amount.
     *
     * @param mintId mint identifier
     * @param amount proof amount
     * @return set of matching proof entities
     * @throws CashuErrorException if none are found
     */
    @GetMapping("/mint/{mintId}/amount/{amount}")
    public ResponseEntity<Set<ProofEntity>> retrieveByMintAndAmount(@PathVariable("mintId") String mintId,
            @PathVariable("amount") Integer amount) throws CashuErrorException {
        log.info("Retrieving ProofEntities by mintId {} and amount {}", mintId, amount);
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_IdAndAmount(UUID.fromString(mintId), amount);
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves a proof by mint and unblinded signature.
     *
     * @param mintId             mint identifier
     * @param unblindedSignature unblinded signature value
     * @return matching proof entity
     * @throws CashuErrorException if no proof exists for the mint/signature pair
     */
    @GetMapping("/mint/{mintId}/signature/{unblindedSignature}")
    public ResponseEntity<ProofEntity> retrieveByMintAndUnblindedSignature(
            @PathVariable("mintId") String mintId,
            @PathVariable("unblindedSignature") String unblindedSignature) throws CashuErrorException {
        log.info("Retrieving ProofEntity by mintId {} and signature {}", mintId, unblindedSignature);
        Optional<ProofEntity> proof = proofRepository.findByMint_IdAndUnblindedSignature(UUID.fromString(mintId),
                unblindedSignature);
        if (proof.isPresent()) {
            log.debug("Retrieved ProofEntity by mintId {} and signature {}", mintId, unblindedSignature);
            return ResponseEntity.ok(proof.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Archives a proof by marking it as archived.
     *
     * @param id proof identifier
     * @return archived proof entity
     * @throws CashuErrorException if the proof does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<ProofEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Archiving ProofEntity {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            var proof = proofOpt.get();
            proof.setArchived(true); // Assumes an 'archived' field
            var archivedProof = proofRepository.save(proof);
            log.debug("Archived ProofEntity {}", archivedProof.getId());
            return ResponseEntity.ok(archivedProof);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a proof entity.
     *
     * @param id proof identifier
     * @return empty response on success
     * @throws CashuErrorException if the proof does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Deleting ProofEntity {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            proofRepository.delete(proofOpt.get());
            log.debug("Deleted ProofEntity {}", proofOpt.get().getId());
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.noContent().build();
    }
}
