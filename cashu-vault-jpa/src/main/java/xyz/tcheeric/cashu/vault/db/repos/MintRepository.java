package xyz.tcheeric.cashu.vault.db.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.UUID;

public interface MintRepository extends JpaRepository<MintEntity, UUID> {
}