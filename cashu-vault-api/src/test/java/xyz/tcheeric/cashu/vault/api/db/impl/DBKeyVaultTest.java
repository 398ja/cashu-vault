package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBKeyVaultTest {

    @Test
    void storeArchiveDeleteUseVaultClient() throws CashuErrorException {
        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId("ks");
        KeyEntity entity = new KeyEntity();
        entity.setKeySet(keySet);

        @SuppressWarnings("unchecked")
        VaultClient<KeyEntity> client = mock(VaultClient.class);
        KeySetVaultClient ksClient = mock(KeySetVaultClient.class);
        when(ksClient.getByKeySetId(anyString())).thenReturn(keySet);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keySetClient).thenReturn(ksClient);

            DBKeyVault vault = new DBKeyVault(client);

            vault.store(entity);
            verify(ksClient).getByKeySetId("ks");
            verify(client).store(entity);

            vault.archive(entity.getId().toString());
            verify(client).archive(entity.getId().toString());

            vault.delete(entity.getId().toString());
            verify(client).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveKeyReturnsWrappedEntity() throws Exception {
        KeyEntity entity = new KeyEntity();
        @SuppressWarnings("unchecked")
        VaultClient<KeyEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);

        DBKeyVault vault = new DBKeyVault(client);
        KeyEntity result = vault.retrieve("id");
        verify(client).retrieve("id");
        assertThat(result).isEqualTo(entity);
    }
}
