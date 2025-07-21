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
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeySetRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/vault/keyset")
public class KeySetVaultController {

    @Autowired
    private KeySetRepository keySetRepository;

    @PostMapping
    public ResponseEntity<KeySetEntity> store(@RequestBody KeySetEntity keySet) throws CashuErrorException {
        KeySetEntity newKeySet = keySetRepository.save(keySet);
        return ResponseEntity.ok(newKeySet);
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeySetEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            return ResponseEntity.ok(keySet.get());
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<KeySetEntity> retrieveByKeySetId(@PathVariable("id") String id) throws CashuErrorException, ExecutionException, InterruptedException {
        Optional<KeySetEntity> keySet = keySetRepository.findByKeySetId(id);
        if (keySet.isPresent()) {
            return ResponseEntity.ok(keySet.get());
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    @GetMapping("/unit/{unit}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByUnit(@PathVariable("unit") String unit) throws CashuErrorException, InterruptedException {
        Optional<Set<KeySetEntity>> keySets = keySetRepository.findByUnit(unit);
        if (keySets.isEmpty()) {
            throw new CashuErrorException("No KeySetEntity found for the specified unit");
        }
        return ResponseEntity.ok(keySets.get());
    }

    @GetMapping("/mint/{mintId}/unit/{unit}/keyset/{keySetId}")
    public ResponseEntity<KeySetEntity> getKeySetByMintIdAndUnit(@PathVariable("mintId") String mintId, @PathVariable("unit") String unit, @PathVariable("keySetId") String keySetId) throws CashuErrorException, InterruptedException {
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

    @GetMapping("/mint/{mintId}")
    public ResponseEntity<Set<KeySetEntity>> getKeySetsByMintId(@PathVariable("mintId") String mintId) throws CashuErrorException, ExecutionException, InterruptedException {
        var keySets = keySetRepository.findByMint_Id(UUID.fromString(mintId));
        if (keySets.isEmpty()) {
            throw new CashuErrorException("No KeySetEntity found for the specified mintId");
        }
        return ResponseEntity.ok(keySets.get());
    }

    @PostMapping("/archive/{id}")
    public ResponseEntity<KeySetEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            KeySetEntity archivedKeySet = keySet.get();
            archivedKeySet.setArchived(true); // Assuming there's an 'archived' field
            keySetRepository.save(archivedKeySet);
            return ResponseEntity.ok(archivedKeySet);
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeySetEntity> keySet = keySetRepository.findById(UUID.fromString(id));
        if (keySet.isPresent()) {
            keySetRepository.delete(keySet.get());
            return ResponseEntity.noContent().build();
        } else {
            throw new CashuErrorException("KeySetEntity not found");
        }
    }
}