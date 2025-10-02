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
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeyRepository;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for managing {@link KeyEntity} resources.
 */
@RestController
@RequestMapping("/vault/key")
@RequiredArgsConstructor
@Slf4j
public class KeyVaultController {

    private final KeyRepository keyRepository;

    /**
     * Stores a new key entity.
     *
     * @param key key to persist
     * @return stored key entity
     * @throws CashuErrorException if the key cannot be persisted
     */
    @PostMapping
    public ResponseEntity<KeyEntity> store(@RequestBody KeyEntity key) throws CashuErrorException {
        log.info("Storing KeyEntity {}", key.getId());
        var savedKey = keyRepository.save(key);
        log.debug("Stored KeyEntity {}", savedKey.getId());
        return ResponseEntity.ok(savedKey);
    }

    /**
     * Retrieves all key entities.
     *
     * @return list of keys (possibly empty)
     */
    @GetMapping
    public ResponseEntity<List<KeyEntity>> list() {
        log.info("Listing all KeyEntity items");
        return ResponseEntity.ok(keyRepository.findAll());
    }

    /**
     * Retrieves a key by its identifier.
     *
     * @param id key identifier
     * @return matching key entity
     * @throws CashuErrorException if no key exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<KeyEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving KeyEntity by id {}", id);
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            log.debug("Retrieved KeyEntity by id {}", keyOpt.get().getId());
            return ResponseEntity.ok(keyOpt.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves keys for a given unit.
     *
     * @param unit monetary unit code
     * @return set of keys for the unit
     * @throws CashuErrorException if no keys are found
     */
    @GetMapping("/unit/{unit}")
    public ResponseEntity<Set<KeyEntity>> getKeysByUnit(@PathVariable("unit") String unit) throws CashuErrorException {
        log.info("Retrieving keys for unit {}", unit);
        Set<KeyEntity> keySet = keyRepository.findByKeySet_UnitIgnoreCase(unit)
                .filter(set -> !set.isEmpty())
                .orElseThrow(() -> new CashuErrorException("No keys found for the specified unit"));
        return ResponseEntity.ok(keySet);
    }

    /**
     * Retrieves a key by its private key value.
     *
     * @param privateKey private key string
     * @return matching key entity
     * @throws CashuErrorException if no key exists for the private key
     */
    @GetMapping("/privatekey/{privateKey}")
    public ResponseEntity<KeyEntity> getKeyByPrivateKey(@PathVariable("privateKey") String privateKey) throws CashuErrorException {
        log.info("Retrieving KeyEntity by privateKey");
        Optional<KeyEntity> keyOpt = keyRepository.findByPrivateKey(privateKey);
        if (keyOpt.isPresent()) {
            log.debug("Retrieved KeyEntity by privateKey");
            return ResponseEntity.ok(keyOpt.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves keys belonging to a specific key set.
     *
     * @param id key set identifier
     * @return set of keys within the key set
     * @throws CashuErrorException if none are found
     */
    @GetMapping("/keyset/{id}")
    public ResponseEntity<Set<KeyEntity>> getKeysByKeySetId(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving keys for keySet {}", id);
        Optional<Set<KeyEntity>> keys = keyRepository.findByKeySet_Id(UUID.fromString(id));
        if (keys.isPresent()) {
            return ResponseEntity.ok(keys.get());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Archives a key entity.
     *
     * @param id key identifier
     * @return archived key entity
     * @throws CashuErrorException if the key does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<KeyEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Archiving KeyEntity {}", id);
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            KeyEntity archivedKey = keyOpt.get();
            archivedKey.setArchived(true); // Assumes an 'archived' field
            var updatedKey = keyRepository.save(archivedKey);
            log.debug("Archived KeyEntity {}", updatedKey.getId());
            return ResponseEntity.ok(updatedKey);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a key entity.
     *
     * @param id key identifier
     * @return empty response on success
     * @throws CashuErrorException if the key does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Deleting KeyEntity {}", id);
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            keyRepository.delete(keyOpt.get());
            log.debug("Deleted KeyEntity {}", keyOpt.get().getId());
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.noContent().build();
    }
}
