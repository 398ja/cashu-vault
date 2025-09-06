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
import xyz.tcheeric.cashu.vault.db.model.BlindSignatureEntity;
import xyz.tcheeric.cashu.vault.db.repos.BlindSignatureRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller exposing CRUD endpoints for {@link BlindSignatureEntity} records.
 */
@RestController
@RequestMapping("/vault/blindsignature")
@RequiredArgsConstructor
@Slf4j
public class BlindSignatureVaultController {

    private final BlindSignatureRepository repository;

    /**
     * Stores a new blind signature entity.
     *
     * @param entity entity to persist
     * @return stored entity
     * @throws CashuErrorException if the entity cannot be persisted
     */
    @PostMapping
    public ResponseEntity<BlindSignatureEntity> store(@RequestBody BlindSignatureEntity entity)
            throws CashuErrorException {
        log.info("Storing BlindSignatureEntity {}", entity.getId());
        var saved = repository.save(entity);
        log.debug("Stored BlindSignatureEntity {}", saved.getId());
        return ResponseEntity.ok(saved);
    }

    /**
     * Retrieves a blind signature by its identifier.
     *
     * @param id entity identifier
     * @return matching entity
     * @throws CashuErrorException if no entity with the ID exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<BlindSignatureEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Retrieving BlindSignatureEntity {}", id);
        Optional<BlindSignatureEntity> entity = repository.findById(UUID.fromString(id));
        if (entity.isPresent()) {
            return ResponseEntity.ok(entity.get());
        }
        throw new CashuErrorException("BlindSignatureEntity not found");
    }

    /**
     * Retrieves a blind signature by its blinded message.
     *
     * @param blindedMessage blinded message value
     * @return matching entity
     * @throws CashuErrorException if none exists
     */
    @GetMapping("/message/{blindedMessage}")
    public ResponseEntity<BlindSignatureEntity> retrieveByMessage(
            @PathVariable("blindedMessage") String blindedMessage) throws CashuErrorException {
        log.info("Retrieving BlindSignatureEntity for message {}", blindedMessage);
        Optional<BlindSignatureEntity> entity = repository.findByBlindedMessage(blindedMessage);
        if (entity.isPresent()) {
            return ResponseEntity.ok(entity.get());
        }
        throw new CashuErrorException("BlindSignatureEntity not found for message");
    }

    /**
     * Retrieves a blind signature by key set id and blinded message.
     *
     * @param keySetId       external key set id
     * @param blindedMessage blinded message value
     * @return matching entity
     * @throws CashuErrorException if none exists
     */
    @GetMapping("/keyset/{keySetId}/message/{blindedMessage}")
    public ResponseEntity<BlindSignatureEntity> retrieveByKeySetAndMessage(
            @PathVariable("keySetId") String keySetId,
            @PathVariable("blindedMessage") String blindedMessage) throws CashuErrorException {
        log.info("Retrieving BlindSignatureEntity for keyset {} and message {}", keySetId, blindedMessage);
        Optional<BlindSignatureEntity> entity =
                repository.findByKeySet_KeySetIdAndBlindedMessage(keySetId, blindedMessage);
        if (entity.isPresent()) {
            return ResponseEntity.ok(entity.get());
        }
        throw new CashuErrorException("BlindSignatureEntity not found for keyset/message");
    }

    /**
     * Retrieves all blind signatures for a key set.
     *
     * @param keySetId internal key set identifier
     * @return set of matching entities
     * @throws CashuErrorException if none are found
     */
    @GetMapping("/keyset/{keySetId}")
    public ResponseEntity<Set<BlindSignatureEntity>> retrieveByKeySet(@PathVariable("keySetId") String keySetId)
            throws CashuErrorException {
        log.info("Retrieving BlindSignatureEntities for keyset {}", keySetId);
        Optional<Set<BlindSignatureEntity>> entities = repository.findByKeySet_Id(UUID.fromString(keySetId));
        if (entities.isPresent() && !entities.get().isEmpty()) {
            return ResponseEntity.ok(entities.get());
        }
        throw new CashuErrorException("No BlindSignatureEntities found for key set");
    }

    /**
     * Archives an entity by marking it as archived.
     *
     * @param id entity identifier
     * @return archived entity
     * @throws CashuErrorException if the entity does not exist
     */
    @PostMapping("/archive/{id}")
    public ResponseEntity<BlindSignatureEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Archiving BlindSignatureEntity {}", id);
        Optional<BlindSignatureEntity> entityOpt = repository.findById(UUID.fromString(id));
        if (entityOpt.isPresent()) {
            var entity = entityOpt.get();
            entity.setArchived(true);
            var archived = repository.save(entity);
            return ResponseEntity.ok(archived);
        }
        throw new CashuErrorException("BlindSignatureEntity not found");
    }

    /**
     * Deletes a blind signature entity.
     *
     * @param id entity identifier
     * @return empty response on success
     * @throws CashuErrorException if the entity does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        log.info("Deleting BlindSignatureEntity {}", id);
        Optional<BlindSignatureEntity> entityOpt = repository.findById(UUID.fromString(id));
        if (entityOpt.isPresent()) {
            repository.delete(entityOpt.get());
            return ResponseEntity.noContent().build();
        }
        throw new CashuErrorException("BlindSignatureEntity not found");
    }
}
