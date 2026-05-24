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
import xyz.tcheeric.cashu.vault.db.dto.TombstoneResponse;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBProofVaultTest {

    @Test
    void storeUsesClient() throws Exception {
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

    @Test
    void archiveSetsArchivedAndPersists() throws Exception {
        ProofEntity entity = new ProofEntity();
        entity.setMint(new MintEntity());

        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        when(client.retrieve(anyString())).thenReturn(entity);
        ProofClient proofClient = mock(ProofClient.class);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            DBProofVault vault = new DBProofVault(client);
            vault.archive(entity.getId().toString());
            verify(client).retrieve(entity.getId().toString());
            verify(proofClient).store(argThat(ProofEntity::isArchived));
        }
    }

    @Test
    void deleteThrowsBecausePhysicalDeletionIsForbidden() {
        // Spec 001 / FR-001 — physical deletion is forbidden. delete(id) now throws.
        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        DBProofVault vault = new DBProofVault(client);
        assertThatThrownBy(() -> vault.delete(UUID.randomUUID().toString()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("tombstone");
    }

    @Test
    void tombstoneDelegatesToProofClient() throws Exception {
        @SuppressWarnings("unchecked")
        VaultClient<ProofEntity> client = mock(VaultClient.class);
        ProofClient proofClient = mock(ProofClient.class);
        TombstoneResponse resp = new TombstoneResponse(
                UUID.randomUUID(), "secret", Instant.now(), "admin");
        when(proofClient.tombstone(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(resp);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::proofClient).thenReturn(proofClient);

            DBProofVault vault = new DBProofVault(client);
            TombstoneResponse out = vault.tombstone("mint-1", "secret-1", "operator action", false);
            verify(proofClient).tombstone("mint-1", "secret-1", "operator action", false);
            assertThat(out).isEqualTo(resp);
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
}
