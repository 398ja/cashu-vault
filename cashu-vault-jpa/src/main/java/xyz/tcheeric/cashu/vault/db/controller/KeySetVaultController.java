package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeySetRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for managing {@link KeySetEntity} resources.
 */
@RestController
@RequestMapping("/vault/keyset")
@RequiredArgsConstructor
@Validated
@Slf4j
public class KeySetVaultController {

    private final KeySetRepository keySetRepository;

    /**
     * Stores a new key set entity.
     *
     * @param keySet key set to persist
     * @return stored key set entity
     * @throws CashuErrorException if the key set cannot be persisted
     */
    @PostMapping
    public ResponseEntity<KeySetEntity> store(@RequestBody KeySetEntity keySet) throws CashuErrorException {
        log.info("Storing KeySetEntity {}", keySet.getId());
        KeySetEntity newKeySet = keySetRepository.save(keySet);
        log.debug("Stored KeySetEntity {}", newKeySet.getId());
        return ResponseEntity.ok(newKeySet);
    }

    /**
     * Retrieves all key set entities.
     *
     * @return list of key sets (possibly empty)
     */
    @GetMapping
    public ResponseEntity<List<KeySetEntity>> list() {
        return ResponseEntity.ok(keySetRepository.findAll());
    }

    /**
     * Retrieves a key set by its identifier.
     *
     * @param id key set identifier
     * @return matching key set entity
     * @throws CashuErrorException if the key set is not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<KeySetEntity> retrieve(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Retrieving KeySetEntity by id {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            log.debug("Retrieved KeySetEntity by id {}", keySet.get().getId());
            return ResponseEntity.ok(keySet.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves a key set by its key set identifier.
     *
     * @param id key set identifier string
     * @return matching key set entity
     * @throws CashuErrorException if none exists
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<KeySetEntity> retrieveByKeySetId(
            @PathVariable("id") @NotBlank @Size(max = 16, message = "KeySet ID exceeds maximum length") String id) throws CashuErrorException {
        log.info("Retrieving KeySetEntity by keySetId {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findByKeySetId(id);
        if (keySet.isPresent()) {
            return ResponseEntity.ok(keySet.get());
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves key sets for a specific unit.
     *
     * @param unit monetary unit code
     * @return set of key sets for the unit
     * @throws CashuErrorException if none exist for the unit
     */
    @GetMapping("/unit/{unit}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByUnit(
            @PathVariable("unit") @NotBlank @Size(max = 10, message = "Unit code exceeds maximum length") String unit) throws CashuErrorException {
        log.info("Retrieving KeySetEntities by unit {}", unit);
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByUnit(unit);
        Set<KeySetEntity> keySetEntities = keySets
                .filter(set -> !set.isEmpty())
                .orElse(new HashSet<>());
        return ResponseEntity.ok(keySetEntities);
    }

    /**
     * Retrieves a key set for the given mint and unit filtered by key set ID.
     *
     * @param mintId   mint identifier
     * @param unit     monetary unit code
     * @param keySetId key set identifier
     * @return matching key set entity
     * @throws CashuErrorException if no matching key set is found
     */
    @GetMapping("/mint/{mintId}/unit/{unit}/keyset/{keySetId}")
    public ResponseEntity<KeySetEntity> getKeySetByMintIdAndUnit(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId,
            @PathVariable("unit") @NotBlank @Size(max = 10, message = "Unit code exceeds maximum length") String unit,
            @PathVariable("keySetId") @NotBlank @Size(max = 16, message = "KeySet ID exceeds maximum length") String keySetId) throws CashuErrorException {
        log.info("Retrieving KeySetEntity by mintId {} and unit {}", mintId, unit);
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByMint_IdAndUnit(UUID.fromString(mintId), unit);
        Set<KeySetEntity> keySetEntities = keySets
                .filter(set -> !set.isEmpty())
                .orElseThrow(() -> new CashuErrorException("No KeySetEntity found for the specified mintId and unit"));
        return keySetEntities.stream()
                .filter(keySet -> keySet.getKeySetId().equals(keySetId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Retrieves key sets associated with a mint.
     *
     * @param mintId mint identifier
     * @return set of key sets for the mint
     * @throws CashuErrorException if no key sets are found
     */
    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByMintId(
            @PathVariable("mintId") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String mintId) throws CashuErrorException {
        log.info("Retrieving KeySetEntities by mintId {}", mintId);
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByMint_Id(UUID.fromString(mintId));
        Set<KeySetEntity> keySetEntities = keySets
                .filter(set -> !set.isEmpty())
                .orElseThrow(() -> new CashuErrorException("No KeySetEntity found for the specified mintId"));
        return ResponseEntity.ok(keySetEntities);
    }

    /**
     * Archives a key set entity.
     *
     * @param id key set identifier
     * @return archived key set entity
     * @throws CashuErrorException if the key set does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<KeySetEntity> archive(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Archiving KeySetEntity {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            KeySetEntity archivedKeySet = keySet.get();
            archivedKeySet.setArchived(true); // Assuming there's an 'archived' field
            keySetRepository.save(archivedKeySet);
            log.debug("Archived KeySetEntity {}", archivedKeySet.getId());
            return ResponseEntity.ok(archivedKeySet);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a key set entity.
     *
     * @param id key set identifier
     * @return empty response on success
     * @throws CashuErrorException if the key set does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format") String id) throws CashuErrorException {
        log.info("Deleting KeySetEntity {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            keySetRepository.delete(keySet.get());
            log.debug("Deleted KeySetEntity {}", keySet.get().getId());
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.noContent().build();
    }
}
