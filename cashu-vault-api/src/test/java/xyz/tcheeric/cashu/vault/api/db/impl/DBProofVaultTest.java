package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBProofVaultTest {

    @Test
    void storeArchiveDeleteUseClients() throws Exception {
        MintEntity mint = new MintEntity();
        ProofEntity entity = new ProofEntity();
        entity.setMint(mint);

        try (MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class,
                (m, ctx) -> when(m.retrieve(anyString())).thenReturn(mint));
             MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                     (m, ctx) -> when(m.retrieve(anyString())).thenReturn(entity))) {
            DBProofVault vault = new DBProofVault(entity);
            VaultClient<ProofEntity> client = vaultMock.constructed().get(0);

            vault.store();
            verify(client).store(entity);

            vault.archive();
            assertThat(proofMock.constructed()).hasSize(3);
            verify(proofMock.constructed().get(1)).retrieve(entity.getId().toString());
            verify(proofMock.constructed().get(0)).store(argThat(ProofEntity::isArchived));

            vault.delete();
            verify(proofMock.constructed().get(2)).delete(entity.getId().toString());
        }
    }

    @Test
    void retrieveProofReturnsWrappedEntity() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getBySecret(anyString())).thenReturn(entity));
             MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class)) {
            DBProofVault vault = DBProofVault.retrieveProof("secret");
            verify(proofMock.constructed().get(0)).getBySecret("secret");
            assertThat(vault.getEntity()).isEqualTo(entity);
        }
    }
}
