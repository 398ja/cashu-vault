package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
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

        try (MockedConstruction<VaultClient> mc = mockConstruction(VaultClient.class,
                (mock, ctx) -> when(mock.retrieve(anyString())).thenReturn(mint))) {
            DBKeySetVault vault = new DBKeySetVault();
            VaultClient<KeySetEntity> client = mc.constructed().get(0);

            vault.store(entity);
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

        try (MockedConstruction<VaultClient> mc = mockConstruction(VaultClient.class,
                (mock, ctx) -> when(mock.retrieve(anyString())).thenReturn(entity))) {
            DBKeySetVault vault = new DBKeySetVault();
            VaultClient<?> client = mc.constructed().get(0);
            KeySetEntity retrieved = vault.retrieve("id");
            verify(client).retrieve("id");
            assertThat(retrieved).isEqualTo(entity);
        }
    }
}
