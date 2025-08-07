package xyz.tcheeric.cashu.vault.db.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for managing {@link KeySetEntity} resources.
 */
@RestController
@RequestMapping("/vault/keyset")
@Slf4j
public class KeySetVaultController {

    @Autowired
    private KeySetRepository keySetRepository;

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
     * Retrieves a key set by its identifier.
     *
     * @param id key set identifier
     * @return matching key set entity
     * @throws CashuErrorException if the key set is not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<KeySetEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving KeySetEntity {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            log.debug("Retrieved KeySetEntity {}", keySet.get().getId());
            return ResponseEntity.ok(keySet.get());
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    /**
     * Retrieves a key set by its key set identifier.
     *
     * @param id key set identifier string
     * @return matching key set entity
     * @throws CashuErrorException if none exists
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<KeySetEntity> retrieveByKeySetId(@PathVariable("id") String id) throws CashuErrorException, ExecutionException, InterruptedException {
        log.info("Retrieving KeySetEntity by keySetId {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findByKeySetId(id);
        if (keySet.isPresent()) {
            return ResponseEntity.ok(keySet.get());
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    /**
     * Retrieves key sets for a specific unit.
     *
     * @param unit monetary unit code
     * @return set of key sets for the unit
     * @throws CashuErrorException if none exist for the unit
     */
    @GetMapping("/unit/{unit}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByUnit(@PathVariable("unit") String unit) throws CashuErrorException, InterruptedException {
        log.info("Retrieving KeySetEntities for unit {}", unit);
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByUnit(unit);
        if (keySets.isEmpty()) {
            throw new CashuErrorException("No KeySetEntity found for the specified unit");
        }
        return ResponseEntity.ok(keySets.get());
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
    public ResponseEntity<KeySetEntity> getKeySetByMintIdAndUnit(@PathVariable("mintId") String mintId, @PathVariable("unit") String unit, @PathVariable("keySetId") String keySetId) throws CashuErrorException, InterruptedException {
        log.info("Retrieving KeySetEntity for mint {} unit {}", mintId, unit);
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByMint_IdAndUnit(UUID.fromString(mintId), unit);
        if (keySets.isEmpty()) {
            throw new CashuErrorException("No KeySetEntity found for the specified mintId and unit");
        }
        return keySets.get().stream()
                .filter(keySet -> keySet.getKeySetId().equals(keySetId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new CashuErrorException("KeySetEntity not found for the specified keySetId"));
    }

    /**
     * Retrieves key sets associated with a mint.
     *
     * @param mintId mint identifier
     * @return set of key sets for the mint
     * @throws CashuErrorException if no key sets are found
     */
    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByMintId(@PathVariable("mintId") String mintId) throws CashuErrorException, ExecutionException, InterruptedException {
        log.info("Retrieving KeySetEntities for mint {}", mintId);
        var keySets = keySetRepository.findByMint_Id(UUID.fromString(mintId));
        if (keySets.isEmpty()) {
            throw new CashuErrorException("No KeySetEntity found for the specified mintId");
        }
        return ResponseEntity.ok(keySets.get());
    }

    /**
     * Archives a key set entity.
     *
     * @param id key set identifier
     * @return archived key set entity
     * @throws CashuErrorException if the key set does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<KeySetEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Archiving KeySetEntity {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            KeySetEntity archivedKeySet = keySet.get();
            archivedKeySet.setArchived(true); // Assuming there's an 'archived' field
            keySetRepository.save(archivedKeySet);
            log.debug("Archived KeySetEntity {}", archivedKeySet.getId());
            return ResponseEntity.ok(archivedKeySet);
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    /**
     * Deletes a key set entity.
     *
     * @param id key set identifier
     * @return empty response on success
     * @throws CashuErrorException if the key set does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Deleting KeySetEntity {}", id);
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            keySetRepository.delete(keySet.get());
            log.debug("Deleted KeySetEntity {}", keySet.get().getId());
            return ResponseEntity.noContent().build();
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }
}