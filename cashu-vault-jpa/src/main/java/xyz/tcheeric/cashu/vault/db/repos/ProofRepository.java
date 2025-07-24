package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProofRepository extends JpaRepository<ProofEntity, UUID> {
    Optional<ProofEntity> findBySecret(String secret);

    Optional<Set<ProofEntity>> findByMint_Id(UUID id);


    Optional<Set<ProofEntity>> findByStateIgnoreCase(String state);
}