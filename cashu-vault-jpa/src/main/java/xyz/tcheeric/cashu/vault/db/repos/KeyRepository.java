package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for accessing {@link KeyEntity} instances.
 */
public interface KeyRepository extends JpaRepository<KeyEntity, UUID> {

    /**
     * Finds keys belonging to a specific key set.
     *
     * @param id identifier of the key set
     * @return optional set of keys
     */
    Optional<Set<KeyEntity>> findByKeySet_Id(UUID id);

    /**
     * Finds keys by their unit value.
     *
     * @param unit monetary unit code
     * @return optional set of keys for the unit
     */
    Optional<Set<KeyEntity>> findByKeySet_UnitIgnoreCase(String unit);

    /**
     * Finds keys that carry no derived public key yet.
     *
     * <p>Rows written before the V12 migration have none, and one cannot be computed in SQL
     * because the private key it derives from lives in HashiCorp Vault. KeyPublicKeyBackfill
     * uses this to stamp them once at startup (issue #146).
     *
     * @return keys still missing a public key, possibly empty
     */
    List<KeyEntity> findByPublicKeyIsNull();

}