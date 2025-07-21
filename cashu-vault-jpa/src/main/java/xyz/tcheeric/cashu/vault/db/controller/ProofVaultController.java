package xyz.tcheeric.cashu.vault.db.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/vault/proof")
@RequiredArgsConstructor
public class ProofVaultController {

    @Autowired
    private ProofRepository proofRepository;

    @PostMapping
    public ResponseEntity<ProofEntity> store(@RequestBody ProofEntity proof) throws CashuErrorException {
        var savedProof = proofRepository.save(proof);
        return ResponseEntity.ok(savedProof);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProofEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            return ResponseEntity.ok(proofOpt.get());
        }
        throw new CashuErrorException("ProofEntity not found");
    }

    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<ProofEntity>> retrieveByMint(@PathVariable("mintId") String mintId) throws CashuErrorException, ExecutionException, InterruptedException {
        Optional<Set<ProofEntity>> proofs = proofRepository.findByMint_Id(UUID.fromString(mintId));
        if (proofs.isPresent() && !proofs.get().isEmpty()) {
            return ResponseEntity.ok(proofs.get());
        }
        throw new CashuErrorException("No ProofEntities found for the given Mint ID");
    }

    @PostMapping("/archive/{id}")
    public ResponseEntity<ProofEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            var proof = proofOpt.get();
            proof.setArchived(true); // Assumes an 'archived' field
            var archivedProof = proofRepository.save(proof);
            return ResponseEntity.ok(archivedProof);
        }
        throw new CashuErrorException("ProofEntity not found");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
        if (proofOpt.isPresent()) {
            proofRepository.delete(proofOpt.get());
            return ResponseEntity.noContent().build();
        }
        throw new CashuErrorException("ProofEntity not found");
    }
}