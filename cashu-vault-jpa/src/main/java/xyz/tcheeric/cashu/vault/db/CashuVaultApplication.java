package xyz.tcheeric.cashu.vault.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

@SpringBootApplication
public class CashuVaultApplication {

    @Value("${vault.baseUrl:http://localhost:3333}")
    private String baseUrl;

    public static void main(String[] args) {
		SpringApplication.run(CashuVaultApplication.class, args);
	}

	@Bean
        public VaultClient<MintEntity> vaultMintClient() {
                return new VaultClient<>(MintEntity.class, baseUrl);
        }

	@Bean
        public VaultClient<KeySetEntity> vaultKeySetClient() {
                return new VaultClient<>(KeySetEntity.class, baseUrl);
        }

	@Bean
        public VaultClient<ProofEntity> vaultProofClient() {
                return new VaultClient<>(ProofEntity.class, baseUrl);
        }

	@Bean
        public VaultClient<KeyEntity> vaultKeyClient() {
                return new VaultClient<>(KeyEntity.class, baseUrl);
        }
}
