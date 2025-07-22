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

@SpringBootApplication
@RequiredArgsConstructor
public class CashuVaultApplication {

    private final VaultBaseProperties vaultBaseProperties;

    public CashuVaultApplication() {
        this.vaultBaseProperties = new VaultBaseProperties();
    }

    public static void main(String[] args) {
        SpringApplication.run(CashuVaultApplication.class, args);
    }

    @Bean
    public VaultClient<MintEntity> vaultMintClient() {
        return new VaultClient<>(MintEntity.class, vaultBaseProperties.getUrl());
    }

    @Bean
    public VaultClient<KeySetEntity> vaultKeySetClient() {
        return new VaultClient<>(KeySetEntity.class, vaultBaseProperties.getUrl());
    }

    @Bean
    public VaultClient<ProofEntity> vaultProofClient() {
        return new VaultClient<>(ProofEntity.class, vaultBaseProperties.getUrl());
    }

    @Bean
    public VaultClient<KeyEntity> vaultKeyClient() {
        return new VaultClient<>(KeyEntity.class, vaultBaseProperties.getUrl());
    }
}
