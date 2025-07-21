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
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeyRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/vault/key")
@RequiredArgsConstructor
public class KeyVaultController {

    @Autowired
    private KeyRepository keyRepository;

    @PostMapping
    public ResponseEntity<KeyEntity> store(@RequestBody KeyEntity key) throws CashuErrorException {
        var savedKey = keyRepository.save(key);
        return ResponseEntity.ok(savedKey);
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeyEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            return ResponseEntity.ok(keyOpt.get());
        } else {
            throw new CashuErrorException("KeyEntity not found");
        }
    }

    @GetMapping("/unit/{unit}")
    public ResponseEntity<Set<KeyEntity>> getKeysByUnit(@PathVariable("unit") String unit) throws CashuErrorException, ExecutionException, InterruptedException {
        Optional<Set<KeyEntity>> keys = keyRepository.findByKeySet_UnitIgnoreCase(unit);
        if (keys.get().isEmpty()) {
            throw new CashuErrorException("No keys found for the specified unit");
        }
        return ResponseEntity.ok(keys.get());
    }

    @GetMapping("/privatekey/{privateKey}")
    public ResponseEntity<KeyEntity> getKeyByPrivateKey(@PathVariable("privateKey") String privateKey) throws CashuErrorException, ExecutionException, InterruptedException {
        Optional<KeyEntity> keyOpt = keyRepository.findByPrivateKey(privateKey);
        if (keyOpt.isPresent()) {
            return ResponseEntity.ok(keyOpt.get());
        } else {
            throw new CashuErrorException("KeyEntity not found for the specified private key");
        }
    }

    @GetMapping("/keyset/{id}")
    public ResponseEntity<Set<KeyEntity>> getKeysByKeySetId(@PathVariable("id") String id) throws CashuErrorException, ExecutionException, InterruptedException {
        Optional<Set<KeyEntity>> keys = keyRepository.findByKeySet_Id(UUID.fromString(id));
        if (keys.isPresent()) {
            return ResponseEntity.ok(keys.get());
        }
        throw new CashuErrorException("No keys found for the specified key set ID");
    }

    @PostMapping("/archive/{id}")
    public ResponseEntity<KeyEntity> archive(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            KeyEntity archivedKey = keyOpt.get();
            archivedKey.setArchived(true); // Assumes an 'archived' field
            var updatedKey = keyRepository.save(archivedKey);
            return ResponseEntity.ok(updatedKey);
        } else {
            throw new CashuErrorException("KeyEntity not found");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) throws CashuErrorException {
        Optional<KeyEntity> keyOpt = keyRepository.findById(UUID.fromString(id));
        if (keyOpt.isPresent()) {
            keyRepository.delete(keyOpt.get());
            return ResponseEntity.noContent().build();
        } else {
            throw new CashuErrorException("KeyEntity not found");
        }
    }
}