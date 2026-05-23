package xyz.tcheeric.cashu.vault.hashi.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HCKeySetVaultTest {

    @Mock
    private VaultClient<KeySetEntity> dbClient;

    @Mock
    private HashiVaultClient hashiClient;

    private HCKeySetVault hcKeySetVault;

    private MintEntity mint;

    @BeforeEach
    void setUp() {
        hcKeySetVault = new HCKeySetVault(dbClient, hashiClient);
        mint = new MintEntity();
    }

    @Test
    void store_resolvesMintAndDelegatesToDbClient() throws CashuErrorException {
        KeySetEntity keySet = createKeySetEntity("ks-001", "sat");
        when(dbClient.store(keySet)).thenReturn(keySet);

        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(mint.getId().toString())).thenReturn(mint);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            KeySetEntity result = hcKeySetVault.store(keySet);

            verify(mintClient).retrieve(mint.getId().toString());
            verify(dbClient).store(keySet);
            assertThat(result.getMint()).isEqualTo(mint);
        }
    }

    @Test
    void store_doesNotInteractWithHashiVault() throws CashuErrorException {
        KeySetEntity keySet = createKeySetEntity("ks-001", "sat");
        when(dbClient.store(keySet)).thenReturn(keySet);

        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(anyString())).thenReturn(mint);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            hcKeySetVault.store(keySet);

            verifyNoInteractions(hashiClient);
        }
    }

    @Test
    void retrieve_returnsEntityFromDbClient() throws CashuErrorException {
        KeySetEntity keySet = createKeySetEntity("ks-001", "sat");
        when(dbClient.retrieve("keyset-id")).thenReturn(keySet);

        KeySetEntity result = hcKeySetVault.retrieve("keyset-id");

        assertThat(result).isEqualTo(keySet);
        verify(dbClient).retrieve("keyset-id");
    }

    @Test
    void retrieve_throwsWhenNotFound() {
        when(dbClient.retrieve("missing-id")).thenReturn(null);

        assertThatThrownBy(() -> hcKeySetVault.retrieve("missing-id"))
                .isInstanceOf(CashuErrorException.class)
                .hasMessage("Keyset not found");
    }

    @Test
    void retrieveByMintIdAndUnit_returnsMatchingKeySet() throws CashuErrorException {
        KeySetEntity ks1 = createKeySetEntity("ks-001", "sat");
        KeySetEntity ks2 = createKeySetEntity("ks-002", "usd");

        KeySetVaultClient ksClient = mock(KeySetVaultClient.class);
        when(ksClient.getByMintId("mint-id")).thenReturn(Set.of(ks1, ks2));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keySetClient).thenReturn(ksClient);

            KeySetEntity result = hcKeySetVault.retrieveByMintIdAndUnit("mint-id", "usd");

            assertThat(result.getUnit()).isEqualTo("usd");
            assertThat(result.getKeySetId()).isEqualTo("ks-002");
        }
    }

    @Test
    void retrieveByMintIdAndUnit_throwsWhenNoMatch() {
        KeySetEntity ks = createKeySetEntity("ks-001", "sat");

        KeySetVaultClient ksClient = mock(KeySetVaultClient.class);
        when(ksClient.getByMintId("mint-id")).thenReturn(Set.of(ks));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keySetClient).thenReturn(ksClient);

            assertThatThrownBy(() -> hcKeySetVault.retrieveByMintIdAndUnit("mint-id", "eur"))
                    .isInstanceOf(CashuErrorException.class)
                    .hasMessageContaining("Keyset not found for mintId: mint-id and unit: eur");
        }
    }

    @Test
    void archive_delegatesToDbClient() throws CashuErrorException {
        KeySetEntity archived = createKeySetEntity("ks-001", "sat");
        archived.setArchived(true);
        when(dbClient.archive("keyset-id")).thenReturn(archived);

        KeySetEntity result = hcKeySetVault.archive("keyset-id");

        verify(dbClient).archive("keyset-id");
        assertThat(result.isArchived()).isTrue();
    }

    @Test
    void delete_delegatesToDbClient() throws CashuErrorException {
        hcKeySetVault.delete("keyset-id");

        verify(dbClient).delete("keyset-id");
    }

    private KeySetEntity createKeySetEntity(String keySetId, String unit) {
        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId(keySetId);
        keySet.setUnit(unit);
        keySet.setMint(mint);
        return keySet;
    }
}
