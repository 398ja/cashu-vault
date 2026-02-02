package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

import java.util.UUID;
import java.util.List;

/**
 * REST controller providing CRUD-style endpoints for {@link MintEntity}
 * resources.
 */
@RequestMapping("/vault/mint")
@RestController
@RequiredArgsConstructor
@Validated
@Slf4j
public class MintVaultController {

    private final MintRepository mintRepository;

    /**
     * Stores a new mint entity.
     *
     * @param mint mint entity to persist
     * @return stored mint entity
     * @throws CashuErrorException if the mint cannot be persisted
     */
    @PostMapping
    public ResponseEntity<MintEntity> store(@RequestBody MintEntity mint) throws CashuErrorException {
        log.info("Storing MintEntity {}", mint.getId());
        var newMint = mintRepository.save(mint);
        log.debug("Stored MintEntity {}", newMint.getId());
        return ResponseEntity.ok(newMint);
    }

    /**
     * Retrieves a mint by its identifier.
     *
     * @param id mint identifier
     * @return matching mint entity
     * @throws CashuErrorException if no mint exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<MintEntity> retrieve(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Retrieving MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        log.debug("Retrieved MintEntity {}", mint.getId());
        return ResponseEntity.ok(mint);
    }

    /**
     * Marks a mint as archived.
     *
     * @param id mint identifier
     * @return archived mint entity
     * @throws CashuErrorException if the mint does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<MintEntity> archive(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Archiving MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mint.setArchived(true);
        var archivedMint = mintRepository.save(mint);
        log.debug("Archived MintEntity {}", archivedMint.getId());
        return ResponseEntity.ok(archivedMint);
    }

    /**
     * Retrieves all mint entities.
     *
     * @return list of all mints (possibly empty)
     */
    @GetMapping
    public ResponseEntity<List<MintEntity>> list() {
        log.info("Listing all MintEntity items");
        return ResponseEntity.ok(mintRepository.findAll());
    }

    /**
     * Deletes a mint entity.
     *
     * @param id mint identifier
     * @return empty response on success
     * @throws CashuErrorException if the mint does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Deleting MintEntity {}", id);
        MintEntity mint = mintRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new CashuErrorException("MintEntity not found"));
        mintRepository.delete(mint);
        log.debug("Deleted MintEntity {}", mint.getId());
        return ResponseEntity.noContent().build();
    }
}

