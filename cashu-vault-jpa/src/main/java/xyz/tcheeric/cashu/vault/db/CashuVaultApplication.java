package xyz.tcheeric.cashu.vault.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

@SpringBootApplication
public class CashuVaultApplication {

    public static void main(String[] args) {
		SpringApplication.run(CashuVaultApplication.class, args);
	}

	@Bean
	public VaultClient<MintEntity> vaultMintClient() {
		return new VaultClient<>(MintEntity.class);
	}

	@Bean
	public VaultClient<KeySetEntity> vaultKeySetClient() {
		return new VaultClient<>(KeySetEntity.class);
	}

	@Bean
	public VaultClient<ProofEntity> vaultProofClient() {
		return new VaultClient<>(ProofEntity.class);
	}

	@Bean
	public VaultClient<KeyEntity> vaultKeyClient() {
		return new VaultClient<>(KeyEntity.class);
	}
}