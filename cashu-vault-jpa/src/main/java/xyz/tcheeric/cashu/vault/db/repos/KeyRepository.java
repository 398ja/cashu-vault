package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface KeyRepository extends JpaRepository<KeyEntity, UUID> {

    Optional<Set<KeyEntity>> findByKeySet_Id(UUID id);

    Optional<Set<KeyEntity>> findByKeySet_UnitIgnoreCase(String unit);

    Optional<KeyEntity> findByPrivateKey(String privateKey);
}