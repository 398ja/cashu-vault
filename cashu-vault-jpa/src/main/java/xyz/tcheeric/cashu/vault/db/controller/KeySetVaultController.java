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

import java.util.Collections;
import java.util.LinkedHashSet;
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

    /**
     * Accepts either NUT-02 keyset id version.
     * <p>
     * A v1 id is 16 hex characters. A v2 id is the version byte {@code 01}
     * followed by a 32-byte SHA-256 hash, so 66 hex characters. This was
     * previously a flat {@code @Size(max = 16)}, which silently made every v2
     * id a 400: the mint would fail to find a keyset that existed, conclude it
     * needed seeding, and then fail again trying to create it. Matching on
     * shape rather than a bare length also rejects non-hex input, which the
     * length cap never did.
     */
    private static final String KEYSET_ID_PATTERN = "^([0-9a-fA-F]{16}|01[0-9a-fA-F]{64})$";

    private static final String KEYSET_ID_MESSAGE =
            "KeySet ID must be 16 hex characters (NUT-02 v1) or 01 followed by 64 hex characters (v2)";

    private final KeySetRepository keySetRepository;

    /**
     * Stores a key set entity.
     *
     * @param keySet key set to persist
     * @return stored key set entity
     * @throws CashuErrorException if the key set cannot be persisted
     */
    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<KeySetEntity> store(@RequestBody KeySetEntity keySet) throws CashuErrorException {
        log.info("Storing KeySetEntity {}", keySet.getId());

        // Carry the existing keys onto the incoming entity before saving.
        //
        // KeySetEntity.keys is @OneToMany(orphanRemoval = true) AND @JsonIgnore, so a
        // key set arriving over REST ALWAYS deserialises with an empty collection. Saving
        // it as-is told Hibernate the set now has no keys, and orphanRemoval deleted every
        // one of them. Re-storing a key set — which callers do idempotently, expecting a
        // no-op — silently destroyed its key material.
        //
        // This is not hypothetical: it wiped all 24 keys of a live keyset on staging,
        // leaving a mint that advertised the keyset with zero keys and could not sign.
        // The private keys survived only because they live in HashiCorp Vault and it was
        // the path rows that were lost.
        //
        // An empty payload therefore means "I am not describing keys", not "this key set
        // has none". Deleting keys is deliberate work and belongs to the key endpoints,
        // which name what they remove.
        //
        // @Transactional is load-bearing, not decoration. `keys` is lazy, so without a
        // session open across the read and the save the collection cannot initialise:
        // the copy would be empty and orphanRemoval would fire regardless. size() forces
        // it while the session is still there.
        if (keySet.getId() != null && keySet.getKeys().isEmpty()) {
            keySetRepository.findById(keySet.getId()).ifPresent(existing -> {
                existing.getKeys().size();
                keySet.setKeys(new LinkedHashSet<>(existing.getKeys()));
            });
        }

        // Never let a store CLEAR the archived flag.
        //
        // Same class of bug as the keys above, found reviewing this fix: `archived` is a
        // plain boolean defaulting to false, so a payload that simply does not mention it
        // deserialises to false and un-archives the key set. A client that round-trips the
        // entity is fine; one that posts a partial body — an older client, a hand-written
        // call — silently puts a retired keyset back into service.
        //
        // That is not a cosmetic flag. ADR 0004 defines archived as "refuses to sign", and
        // it is the mechanism behind keyset rotation and mint retirement.
        //
        // Asymmetric on purpose: false -> true is honoured, true -> false is not. Archiving
        // through this endpoint stays possible, while un-archiving requires a deliberate
        // call to an endpoint that names what it is doing, exactly as deleting keys does.
        // There is no such endpoint today, which is the correct default for an operation
        // that returns a retired signing key to service.
        if (keySet.getId() != null && !keySet.isArchived()) {
            keySetRepository.findById(keySet.getId())
                    .filter(KeySetEntity::isArchived)
                    .ifPresent(existing -> {
                        log.info("Refusing to un-archive KeySetEntity {} on store; "
                                + "archived is cleared only by a deliberate call", keySet.getId());
                        keySet.setArchived(true);
                    });
        }

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
            @PathVariable("id") @NotBlank @Pattern(regexp = KEYSET_ID_PATTERN, message = KEYSET_ID_MESSAGE) String id) throws CashuErrorException {
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
                .orElse(Collections.emptySet());
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
            @PathVariable("keySetId") @NotBlank @Pattern(regexp = KEYSET_ID_PATTERN, message = KEYSET_ID_MESSAGE) String keySetId) throws CashuErrorException {
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
