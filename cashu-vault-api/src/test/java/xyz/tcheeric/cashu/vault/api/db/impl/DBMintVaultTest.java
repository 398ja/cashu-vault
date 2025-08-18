package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBMintVaultTest {

    @Test
    void storeArchiveDeleteUseVaultClient() throws CashuErrorException {
        MintEntity entity = new MintEntity();
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> client = mock(VaultClient.class);

        DBMintVault vault = new DBMintVault(client);

        vault.store(entity);
        verify(client).store(entity);

        vault.archive(entity.getId().toString());
        verify(client).archive(entity.getId().toString());

        vault.delete(entity.getId().toString());
        verify(client).delete(entity.getId().toString());
    }

    @Test
    void retrieveMintReturnsWrappedEntity() throws Exception {
        MintEntity entity = new MintEntity();
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);

        DBMintVault vault = new DBMintVault(client);
        MintEntity result = vault.retrieve("42");
        verify(client).retrieve("42");
        assertThat(result).isEqualTo(entity);
    }
}
