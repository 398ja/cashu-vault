package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface KeySetRepository extends JpaRepository<KeySetEntity, UUID> {

    Optional<KeySetEntity> findByKeySetId(String keySetId);

    Optional<Set<KeySetEntity>> findByUnit(String unit);

    Optional<Set<KeySetEntity>> findByMint_Id(UUID mintId);

    Optional<Set<KeySetEntity>> findByMint_IdAndUnit(UUID uuid, String unit);
}