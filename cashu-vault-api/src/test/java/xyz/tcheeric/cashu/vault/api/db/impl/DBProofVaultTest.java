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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBProofVaultTest {

    // Storing a proof resolves its mint and inserts it through the generic client.
    @Test
    void storeResolvesTheMintAndInserts() throws Exception {
        MintEntity mint = new MintEntity();
        ProofEntity entity = new ProofEntity();
        entity.setMint(mint);

        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        @SuppressWarnings("unchecked")
        VaultClient<MintEntity> mintClient = mock(VaultClient.class);
        when(mintClient.retrieve(anyString())).thenReturn(mint);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            DBProofVault vault = new DBProofVault(client);

            vault.store(entity);
            verify(mintClient).retrieve(mint.getId().toString());
            verify(client).store(entity);
        }
    }

    // Archiving calls the archive endpoint and never re-posts the row to the insert-only store.
    @Test
    void archiveUsesTheArchiveEndpointNotStore() throws Exception {
        ProofClient proofClient = mock(ProofClient.class);
        ProofEntity archived = new ProofEntity();
        archived.setArchived(true);
        when(proofClient.archive("proof-id")).thenReturn(archived);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            ProofEntity result = new DBProofVault(proofClient).archive("proof-id");

            assertThat(result.isArchived()).isTrue();
            verify(proofClient, never()).store(any());
        }
    }

    // Deleting a proof is refused on the client too, and no DELETE request is ever sent.
    @Test
    void deleteIsRefusedWithoutCallingTheVault() {
        ProofClient proofClient = mock(ProofClient.class);

        assertThatThrownBy(() -> new DBProofVault(proofClient).delete("proof-id"))
                .isInstanceOf(CashuErrorException.class)
                .hasMessageContaining("cannot be deleted");
        verifyNoInteractions(proofClient);
    }

    // Invalidating a proof asks the vault to mark it spent by mint and secret, and never
    // re-posts the whole row with its state changed.
    @Test
    void invalidateMarksSpentWithoutOverwritingTheRow() throws Exception {
        MintEntity mint = new MintEntity();
        ProofEntity stored = new ProofEntity();
        stored.setMint(mint);
        stored.setSecret("y-point");
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.retrieve(stored.getId().toString())).thenReturn(stored);
        when(proofClient.markSpent(mint.getId().toString(), java.util.List.of("y-point"))).thenReturn(1);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            ProofEntity result = new DBProofVault(proofClient).invalidate(stored.getId().toString());

            assertThat(result.getState()).isEqualTo(ProofEntity.STATE_SPENT);
            verify(proofClient).markSpent(mint.getId().toString(), java.util.List.of("y-point"));
            verify(proofClient, never()).store(any());
        }
    }

    // Invalidation fails loudly when the vault does not report the proof as spent afterwards.
    @Test
    void invalidateFailsWhenTheProofIsNotSpentAfterwards() {
        MintEntity mint = new MintEntity();
        ProofEntity stored = new ProofEntity();
        stored.setMint(mint);
        stored.setSecret("y-point");
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.retrieve(stored.getId().toString())).thenReturn(stored);
        when(proofClient.markSpent(anyString(), any())).thenReturn(0);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            assertThatThrownBy(() -> new DBProofVault(proofClient).invalidate(stored.getId().toString()))
                    .isInstanceOf(CashuErrorException.class);
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
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintIdAndSecret(anyString(), anyString())).thenReturn(entity);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            ProofEntity proofEntity = DBProofVault.retrieveProof("mint", "secret");

            verify(proofClient).getByMintIdAndSecret("mint", "secret");
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofByMintAndAmountUsesClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintAndAmount(anyString(), anyInt())).thenReturn(entity);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            ProofEntity proofEntity = DBProofVault.retrieveProof("mint", 1);
            verify(proofClient).getByMintAndAmount("mint", 1);
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

    @Test
    void retrieveProofByMintAndSignatureUsesClient() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        try (MockedConstruction<ProofClient> proofMock = mockConstruction(ProofClient.class,
                (m, ctx) -> when(m.getByMintAndUnblindedSignature(anyString(), anyString())).thenReturn(entity))) {
            ProofEntity proofEntity = DBProofVault.retrieveProofByUnblindedSignature("mint", "sig");
            verify(proofMock.constructed().get(0)).getByMintAndUnblindedSignature("mint", "sig");
            assertThat(proofEntity).isEqualTo(entity);
        }
    }

/*
    @Test
    void retrieveProofBySecretThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getBySecret(anyString())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("missing", proofClient));
        verify(proofClient).getBySecret("missing");
    }
*/

/*
    @Test
    void retrieveProofByMintAndSecretThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintIdAndSecret(anyString(), anyString())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("mint", "missing", proofClient));
        verify(proofClient).getByMintIdAndSecret("mint", "missing");
    }
*/

/*
    @Test
    void retrieveProofByMintAndAmountThrowsWhenMissing() {
        ProofClient proofClient = mock(ProofClient.class);
        when(proofClient.getByMintAndAmount(anyString(), anyInt())).thenReturn(null);

        assertThrows(CashuErrorException.class,
                () -> DBProofVault.retrieveProof("mint", 1, proofClient));
        verify(proofClient).getByMintAndAmount("mint", 1);
    }
*/

    /*
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
*/
}
