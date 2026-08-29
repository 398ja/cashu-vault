package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBKeySetVaultTest {

    @Test
    void storeArchiveDeleteUseVaultClient() throws CashuErrorException {
        MintEntity mint = new MintEntity();
        KeySetEntity entity = new KeySetEntity();
        entity.setMint(mint);

        @SuppressWarnings("unchecked")
        VaultClient<KeySetEntity> client = mock(VaultClient.class);
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(anyString())).thenReturn(mint);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            DBKeySetVault vault = new DBKeySetVault(client);

            vault.store(entity);
            verify(mintClient).retrieve(anyString());
            verify(client).store(entity);

            vault.archive(entity.getId().toString());
            verify(client).archive(entity.getId().toString());

            vault.delete(entity.getId().toString());
            verify(client).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveKeySetReturnsWrappedEntity() throws Exception {
        KeySetEntity entity = new KeySetEntity();
        @SuppressWarnings("unchecked")
        VaultClient<KeySetEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);

        DBKeySetVault vault = new DBKeySetVault(client);
        KeySetEntity result = vault.retrieve("id");
        verify(client).retrieve("id");
        assertThat(result).isEqualTo(entity);
    }

    /**
     * Checks that the NUT-02 fee stored on a keyset survives being loaded. The mint charges
     * swaps from the loaded keyset and publishes it on /v1/keysets, so a fee dropped here
     * would silently price every transaction at zero however the operator configured it.
     */
    @Test
    void loadCarriesTheConfiguredInputFee() throws Exception {
        KeySetEntity entity = new KeySetEntity();
        entity.setId(UUID.randomUUID());
        entity.setKeySetId("009a1f293253e41e");
        entity.setUnit("sat");
        entity.setInputFeePpk(100);

        KeyVaultClient keyClient = mock(KeyVaultClient.class);
        when(keyClient.getKeysByKeySetId(anyString())).thenReturn(Set.of());

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(mock(xyz.tcheeric.cashu.vault.api.KeyVault.class));

            KeySet loaded = DBKeySetVault.load(entity, false);

            assertThat(loaded.getPartPerThousand())
                .as("the fee an operator configured must reach the mint that charges it")
                .isEqualTo(100);
        }
    }

    /**
     * Checks that a keyset nobody has priced loads as free. Fees are off unless configured,
     * so an unconfigured mint keeps behaving exactly as it did before fees existed.
     */
    @Test
    void loadDefaultsToNoFee() throws Exception {
        KeySetEntity entity = new KeySetEntity();
        entity.setId(UUID.randomUUID());
        entity.setKeySetId("009a1f293253e41e");
        entity.setUnit("sat");

        KeyVaultClient keyClient = mock(KeyVaultClient.class);
        when(keyClient.getKeysByKeySetId(anyString())).thenReturn(Set.of());

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(mock(xyz.tcheeric.cashu.vault.api.KeyVault.class));

            assertThat(DBKeySetVault.load(entity, false).getPartPerThousand())
                .as("a keyset nobody priced charges nothing")
                .isZero();
        }
    }
}
