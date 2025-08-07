package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProofRepository extends JpaRepository<ProofEntity, UUID> {
    Optional<ProofEntity> findBySecret(String secret);

    Optional<Set<ProofEntity>> findByMint_Id(UUID id);


    Optional<Set<ProofEntity>> findByStateIgnoreCase(String state);
}