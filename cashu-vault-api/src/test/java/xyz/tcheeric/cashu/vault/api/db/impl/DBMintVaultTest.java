package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBMintVaultTest {

    @Test
    void storeArchiveDeleteUseVaultClient() {
        MintEntity entity = new MintEntity();

        try (MockedConstruction<VaultClient> mc = mockConstruction(VaultClient.class)) {
            DBMintVault vault = new DBMintVault();
            VaultClient<MintEntity> client = mc.constructed().get(0);

            vault.store(entity);
            verify(client).store(entity);

            vault.archive(entity.getId().toString());
            verify(client).archive(entity.getId().toString());

            vault.delete(entity.getId().toString());
            verify(client).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveMintReturnsWrappedEntity() throws Exception {
        MintEntity entity = new MintEntity();

        try (MockedConstruction<VaultClient> mc = mockConstruction(VaultClient.class,
                (mock, ctx) -> when(mock.retrieve(anyString())).thenReturn(entity))) {
            DBMintVault vault = new DBMintVault();
            VaultClient<?> client = mc.constructed().get(0);
            MintEntity retrieved = vault.retrieve("42");
            verify(client).retrieve("42");
            assertThat(retrieved).isEqualTo(entity);
        }
    }
}
