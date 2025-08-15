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
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.Optional;
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

    /**
     * Stores a new proof entity.
     *
     * @param proof proof to persist
     * @return stored proof entity
     * @throws CashuErrorException in case the entity cannot be persisted
     */
    @PostMapping
    public ResponseEntity<ProofEntity> store(@RequestBody ProofEntity proof) throws CashuErrorException {
        log.info("Storing ProofEntity {}", proof.getId());
        var savedProof = proofRepository.save(proof);
        log.debug("Stored ProofEntity {}", savedProof.getId());
        return ResponseEntity.ok(savedProof);
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
        log.info("Retrieving ProofEntity {}", id);
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            log.debug("Retrieved ProofEntity {}", proofOpt.get().getId());
            return ResponseEntity.ok(proofOpt.get());
        }
        throw new CashuErrorException("ProofEntity not found");
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
        log.info("Retrieving ProofEntities for mint {}", mintId);
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_Id(UUID.fromString(mintId));
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        throw new CashuErrorException("No ProofEntities found for the given Mint ID");
    }

    /**
     * Retrieves a proof by mint and secret.
     *
     * @param mintId mint identifier
     * @param secret secret value
     * @return matching proof entity
     * @throws CashuErrorException if no proof exists for the criteria
     */
    @GetMapping("/mint/{mintId}/secret/{secret}")
    public ResponseEntity<ProofEntity> retrieveByMintAndSecret(@PathVariable("mintId") String mintId,
                                                               @PathVariable("secret") String secret)
            throws CashuErrorException {
        log.info("Retrieving ProofEntity for mint {} with secret {}", mintId, secret);
        Optional<ProofEntity> proof =
                proofRepository.findByMint_IdAndSecret(UUID.fromString(mintId), secret);
        if (proof.isPresent()) {
            return ResponseEntity.ok(proof.get());
        }
        throw new CashuErrorException("ProofEntity not found for the specified mint and secret");
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
                                                                    @PathVariable("amount") Integer amount)
            throws CashuErrorException {
        log.info("Retrieving ProofEntities for mint {} with amount {}", mintId, amount);
        Optional<Set<ProofEntity>> proofs =
                proofRepository.findByMint_IdAndAmount(UUID.fromString(mintId), amount);
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        throw new CashuErrorException("No ProofEntities found for the specified mint and amount");
    }

    /**
     * Retrieves a proof by mint and unblinded signature.
     *
     * @param mintId mint identifier
     * @param unblindedSignature unblinded signature
     * @return matching proof entity
     * @throws CashuErrorException if none is found
     */
    @GetMapping("/mint/{mintId}/signature/{signature}")
    public ResponseEntity<ProofEntity> retrieveByMintAndSignature(@PathVariable("mintId") String mintId,
                                                                  @PathVariable("signature") String unblindedSignature)
            throws CashuErrorException {
        log.info("Retrieving ProofEntity for mint {} with signature {}", mintId, unblindedSignature);
        Optional<ProofEntity> proof =
                proofRepository.findByMint_IdAndUnblindedSignature(UUID.fromString(mintId), unblindedSignature);
        if (proof.isPresent()) {
            return ResponseEntity.ok(proof.get());
        }
        throw new CashuErrorException("ProofEntity not found for the specified mint and signature");
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
        log.info("Retrieving ProofEntity with secret {}", secret);
        Optional<ProofEntity> proof = proofRepository.findBySecret(secret);
        if (proof.isPresent()) {
            log.debug("Retrieved ProofEntity {}", proof.get().getId());
            return ResponseEntity.ok(proof.get());
        }
        throw new CashuErrorException("ProofEntity not found for the specified secret");
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
        throw new CashuErrorException("ProofEntity not found");
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
        throw new CashuErrorException("ProofEntity not found");
    }
}
