package xyz.tcheeric.cashu.vault.hashi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;
import xyz.tcheeric.cashu.vault.hashi.impl.HCKeySetVault;
import xyz.tcheeric.cashu.vault.hashi.impl.HCKeyVault;

/**
 * Registers HashiCorp Vault implementations with the VaultClientFactory
 * and switches the active backend when vault.backend=hashicorp.
 */
@Component
@ConditionalOnProperty(name = "vault.backend", havingValue = "hashicorp")
@RequiredArgsConstructor
@Slf4j
public class HashiVaultRegistrar {

    private final HashiVaultClient hashiClient;

    @PostConstruct
    public void register() {
        log.info("Registering HashiCorp Vault implementations");

        VaultClientFactory.registerHCVault(KeyEntity.class,
                client -> new HCKeyVault(client, hashiClient));
        VaultClientFactory.registerHCVault(KeySetEntity.class,
                client -> new HCKeySetVault(client, hashiClient));

        VaultClientFactory.setBackend(VaultClientFactory.Backend.HASHICORP);
        log.info("Active secrets backend set to HASHICORP");
    }
}
