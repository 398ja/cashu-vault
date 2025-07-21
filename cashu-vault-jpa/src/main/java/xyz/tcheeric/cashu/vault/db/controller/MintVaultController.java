package xyz.tcheeric.cashu.vault.db.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

import java.util.UUID;

@RequestMapping("/vault/mint")
@RestController
@Slf4j
public class MintVaultController {

    @Autowired
    private MintRepository mintRepository;

    @PostMapping
    public ResponseEntity<MintEntity> store(@RequestBody MintEntity mint) throws CashuErrorException {
        log.info("Storing MintEntity {}", mint.getId());
        var newMint = mintRepository.save(mint);
        log.debug("Stored MintEntity {}", newMint.getId());
        return ResponseEntity.ok(newMint);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MintEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        log.debug("Retrieved MintEntity {}", mint.getId());
        return ResponseEntity.ok(mint);
    }

    @PostMapping("/archive/{id}")
    public ResponseEntity<MintEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Archiving MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mint.setArchived(true);
        var archivedMint = mintRepository.save(mint);
        log.debug("Archived MintEntity {}", archivedMint.getId());
        return ResponseEntity.ok(archivedMint);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Deleting MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mintRepository.delete(mint);
        log.debug("Deleted MintEntity {}", mint.getId());
        return ResponseEntity.noContent().build();
    }


    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure", ex);
        return "Conflict detected: " + ex.getMessage();
    }
}


