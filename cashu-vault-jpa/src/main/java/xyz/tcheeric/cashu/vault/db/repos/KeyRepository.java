package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface KeyRepository extends JpaRepository<KeyEntity, UUID> {

    Optional<Set<KeyEntity>> findByKeySet_Id(UUID id);

    Optional<Set<KeyEntity>> findByKeySet_UnitIgnoreCase(String unit);

    Optional<KeyEntity> findByPrivateKey(String privateKey);
}
