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
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

import java.util.UUID;

@RequestMapping("/vault/mint")
@RestController
public class MintVaultController {

    @Autowired
    private MintRepository mintRepository;

    @PostMapping
    public ResponseEntity<MintEntity> store(@RequestBody MintEntity mint) throws CashuErrorException {
        var newMint = mintRepository.save(mint);
        return ResponseEntity.ok(newMint);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MintEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        return ResponseEntity.ok(mint);
    }

    @PostMapping("/archive/{id}")
    public ResponseEntity<MintEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mint.setArchived(true);
        var archivedMint = mintRepository.save(mint);
        return ResponseEntity.ok(archivedMint);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mintRepository.delete(mint);
        return ResponseEntity.noContent().build();
    }


    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return "Conflict detected: " + ex.getMessage();
    }
}


