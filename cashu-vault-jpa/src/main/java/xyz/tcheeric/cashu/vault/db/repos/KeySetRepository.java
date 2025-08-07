package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for performing CRUD operations on {@link KeySetEntity}.
 */
public interface KeySetRepository extends JpaRepository<KeySetEntity, UUID> {

    /**
     * Finds a key set by its identifier string.
     *
     * @param keySetId key set identifier
     * @return optional key set entity
     */
    Optional<KeySetEntity> findByKeySetId(String keySetId);

    /**
     * Finds key sets by unit.
     *
     * @param unit monetary unit code
     * @return optional set of key sets
     */
    Optional<Set<KeySetEntity>> findByUnit(String unit);

    /**
     * Finds key sets associated with a mint.
     *
     * @param mintId mint identifier
     * @return optional set of key sets
     */
    Optional<Set<KeySetEntity>> findByMint_Id(UUID mintId);

    /**
     * Finds key sets by mint and unit.
     *
     * @param uuid mint identifier
     * @param unit monetary unit code
     * @return optional set of key sets
     */
    Optional<Set<KeySetEntity>> findByMint_IdAndUnit(UUID uuid, String unit);
}