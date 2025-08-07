package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for {@link ProofEntity} storage operations.
 */
public interface ProofRepository extends JpaRepository<ProofEntity, UUID> {

    /**
     * Finds a proof by its secret value.
     *
     * @param secret proof secret
     * @return optional proof entity
     */
    Optional<ProofEntity> findBySecret(String secret);

    /**
     * Finds proofs belonging to the specified mint.
     *
     * @param id mint identifier
     * @return optional set of proof entities
     */
    Optional<Set<ProofEntity>> findByMint_Id(UUID id);

    /**
     * Finds proofs by their state.
     *
     * @param state proof state
     * @return optional set of proofs in that state
     */
    Optional<Set<ProofEntity>> findByStateIgnoreCase(String state);
}