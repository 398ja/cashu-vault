package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
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
    void storeArchiveDeleteUseVaultClient() {
        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId("ks");
        KeyEntity entity = new KeyEntity();
        entity.setKeySet(keySet);

        try (MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class);
             MockedConstruction<KeySetVaultClient> ksMock = mockConstruction(KeySetVaultClient.class,
                     (m, ctx) -> when(m.getByKeySetId(anyString())).thenReturn(keySet));
             MockedConstruction<KeyVaultClient> kvMock = mockConstruction(KeyVaultClient.class)) {
            DBKeyVault vault = new DBKeyVault(entity);
            VaultClient<KeyEntity> client = vaultMock.constructed().get(0);

            vault.store();
            verify(ksMock.constructed().get(0)).getByKeySetId("ks");
            verify(client).store(entity);

            vault.archive();
            verify(kvMock.constructed().get(0)).archive(entity.getId().toString());

            vault.delete();
            verify(kvMock.constructed().get(1)).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveKeyReturnsWrappedEntity() throws Exception {
        KeyEntity entity = new KeyEntity();

        try (MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class);
             MockedConstruction<KeyVaultClient> kvMock = mockConstruction(KeyVaultClient.class,
                     (m, ctx) -> when(m.retrieve(anyString())).thenReturn(entity))) {
            DBKeyVault vault = DBKeyVault.retrieveKey("id");
            verify(kvMock.constructed().get(0)).retrieve("id");
            assertThat(vault.getEntity()).isEqualTo(entity);
        }
    }
}
