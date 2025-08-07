package xyz.tcheeric.cashu.vault.db;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.config.VaultBaseProperties;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

/**
 * Spring Boot application configuration for the Cashu vault.
 */
@SpringBootApplication
@RequiredArgsConstructor
public class CashuVaultApplication {

    private final VaultBaseProperties vaultBaseProperties;

    /**
     * Default constructor initializing configuration properties.
     */
    public CashuVaultApplication() {
        this.vaultBaseProperties = new VaultBaseProperties();
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CashuVaultApplication.class, args);
    }

    /**
     * Provides a client for mint entities.
     *
     * @return vault client for mints
     */
    @Bean
    public VaultClient<MintEntity> vaultMintClient() {
        return new VaultClient<>(MintEntity.class, vaultBaseProperties.getUrl());
    }

    /**
     * Provides a client for key set entities.
     *
     * @return vault client for key sets
     */
    @Bean
    public VaultClient<KeySetEntity> vaultKeySetClient() {
        return new VaultClient<>(KeySetEntity.class, vaultBaseProperties.getUrl());
    }

    /**
     * Provides a client for proof entities.
     *
     * @return vault client for proofs
     */
    @Bean
    public VaultClient<ProofEntity> vaultProofClient() {
        return new VaultClient<>(ProofEntity.class, vaultBaseProperties.getUrl());
    }

    /**
     * Provides a client for key entities.
     *
     * @return vault client for keys
     */
    @Bean
    public VaultClient<KeyEntity> vaultKeyClient() {
        return new VaultClient<>(KeyEntity.class, vaultBaseProperties.getUrl());
    }
}
