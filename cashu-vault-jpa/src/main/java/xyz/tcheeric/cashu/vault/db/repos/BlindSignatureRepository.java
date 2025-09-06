package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.BlindSignatureEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for {@link BlindSignatureEntity} operations.
 */
public interface BlindSignatureRepository extends JpaRepository<BlindSignatureEntity, UUID> {

    /**
     * Finds a record by its blinded message.
     *
     * @param blindedMessage blinded message value
     * @return optional blind signature entity
     */
    Optional<BlindSignatureEntity> findByBlindedMessage(String blindedMessage);

    /**
     * Finds all records for the given key set.
     *
     * @param id key set identifier
     * @return optional set of blind signature entities
     */
    Optional<Set<BlindSignatureEntity>> findByKeySet_Id(UUID id);

    /**
     * Finds a record by key set id and blinded message.
     *
     * @param keySetId       external key set id
     * @param blindedMessage blinded message value
     * @return optional blind signature entity
     */
    Optional<BlindSignatureEntity> findByKeySet_KeySetIdAndBlindedMessage(String keySetId, String blindedMessage);
}
