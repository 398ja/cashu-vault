package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBProofVaultTest {

    @Test
    void storeArchiveDeleteUseClients() throws Exception {
        MintEntity mint = new MintEntity();
        ProofEntity entity = new ProofEntity();
        entity.setMint(mint);

        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(anyString())).thenReturn(mint);
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.retrieve(anyString())).thenReturn(entity);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            DBProofVault vault = new DBProofVault(client);

            vault.store(entity);
            verify(client).store(entity);

            vault.archive(entity.getId().toString());
            verify(client).retrieve(entity.getId().toString());
            verify(proofClient).store(argThat(ProofEntity::isArchived));

            vault.delete(entity.getId().toString());
            verify(proofClient).delete(entity.getId().toString());
        }
    }

    @Test
    void storePendingSetsStatePending() throws Exception {
        MintEntity mint = new MintEntity();
        ProofEntity entity = new ProofEntity();
        entity.setMint(mint);

        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        when(client.store(any())).thenAnswer(invocation -> invocation.getArgument(0));
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(anyString())).thenReturn(mint);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(ProofEntity.class)).thenReturn(client);
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            DBProofVault vault = new DBProofVault(mock(VaultClient.class));

            ProofEntity stored = vault.storePending(entity);
            verify(client).store(entity);
            assertThat(stored.getState()).isEqualTo(ProofEntity.STATE_PENDING);
        }
    }

    @Test
    void retrieveProofBySecretReturnsWrappedEntity() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getBySecret(anyString())).thenReturn(entity);

        ProofEntity result = DBProofVault.retrieveProof("secret", proofClient);
        verify(proofClient).getBySecret("secret");
        assertThat(result).isEqualTo(entity);
    }

    @Test
    void retrieveProofBySecretUsesFactoryClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getBySecret("secret")).thenReturn(entity);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            ProofEntity proofEntity = DBProofVault.retrieveProof("secret");

            factory.verify(VaultClientFactory::proofClient);
            verify(proofClient).getBySecret("secret");
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofByMintAndSecretUsesClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getByMintIdAndSecret(anyString(), anyString())).thenReturn(entity));
             MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class)) {
            ProofEntity proofEntity = DBProofVault.retrieveProof("mint", "secret");
            verify(proofMock.constructed().get(0)).getByMintIdAndSecret("mint", "secret");
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofByMintAndAmountUsesClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getByMintAndAmount(anyString(), anyInt())).thenReturn(entity));
             MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class)) {
            ProofEntity proofEntity = DBProofVault.retrieveProof("mint", 1);
            verify(proofMock.constructed().get(0)).getByMintAndAmount("mint", 1);
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofByMintAndSignatureUsesClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getByMintAndUnblindedSignature(anyString(), anyString())).thenReturn(entity));
             MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class)) {
            ProofEntity proofEntity = DBProofVault.retrieveProofByUnblindedSignature("mint", "sig");
            verify(proofMock.constructed().get(0)).getByMintAndUnblindedSignature("mint", "sig");
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofBySecretThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getBySecret(anyString())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("missing", proofClient));
        verify(proofClient).getBySecret("missing");
    }

    @Test
    void retrieveProofByMintAndSecretThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintIdAndSecret(anyString(), anyString())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("mint", "missing", proofClient));
        verify(proofClient).getByMintIdAndSecret("mint", "missing");
    }

    @Test
    void retrieveProofByMintAndAmountThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintAndAmount(anyString(), anyInt())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("mint", 1, proofClient));
        verify(proofClient).getByMintAndAmount("mint", 1);
    }

    @Test
    void retrieveProofByMintAndSignatureThrowsWhenMissing() {
        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getByMintAndUnblindedSignature(anyString(), anyString())).thenReturn(null));
             MockedConstruction<VaultClient> vaultMock = mockConstruction(VaultClient.class)) {
            assertThrows(CashuErrorException.class,
                    () -> DBProofVault.retrieveProofByUnblindedSignature("mint", "sig"));
            verify(proofMock.constructed().get(0)).getByMintAndUnblindedSignature("mint", "sig");
        }
    }
}
