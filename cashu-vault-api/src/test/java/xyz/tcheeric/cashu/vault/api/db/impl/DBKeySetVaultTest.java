package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBKeySetVaultTest {

    @Test
    void storeArchiveDeleteUseVaultClient() {
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

            DBKeySetVault vault = new DBKeySetVault(entity, client);

            vault.store();
            verify(mintClient).retrieve(anyString());
            verify(client).store(entity);

            vault.archive();
            verify(client).archive(entity.getId().toString());

            vault.delete();
            verify(client).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveKeySetReturnsWrappedEntity() throws Exception {
        KeySetEntity entity = new KeySetEntity();
        @SuppressWarnings("unchecked")
        VaultClient<KeySetEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);

        DBKeySetVault vault = DBKeySetVault.retrieveKeySet("id", client);
        verify(client).retrieve("id");
        assertThat(vault.getEntity()).isEqualTo(entity);
    }
}
