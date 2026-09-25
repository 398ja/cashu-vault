package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Mint;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.KeyVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.List;
import java.util.Set;

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

    /**
     * archive on the list overload selects a keyset GENERATION, not a set of mints.
     *
     * <p>The fixture is the case that matters and that a mint-level filter silently breaks:
     * a single NON-archived mint owning one active and one archived keyset. That is ordinary
     * NUT-02 rotation, and on the one real deployment measured it was 3 of 4 keysets. A
     * filter on mintEntity.isArchived() runs before the keyset loop, so with no archived
     * mints load(true) returns empty and the archived keyset becomes unreachable through the
     * only API that exposes it, which would stop the mint redeeming proofs it must honour.
     *
     * <p>Both directions are asserted: load(false) must surface only the active keyset and
     * load(true) only the archived one, so neither a hardcoded filter nor a dropped filter
     * passes.
     */
    @Test
    void loadSelectsTheKeysetGenerationAndKeepsArchivedKeysetsOnActiveMints() {
        MintEntity activeMint = new MintEntity();
        KeySetEntity activeKeySet = keySetOf(activeMint, "active-keyset-id", false);
        KeySetEntity archivedKeySet = keySetOf(activeMint, "archived-keyset-id", true);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            @SuppressWarnings("unchecked")
            VaultClient<MintEntity> mintClient = mock(VaultClient.class);
            when(mintClient.retrieveAll()).thenReturn(List.of(activeMint));
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            KeySetVaultClient keySetClient = mock(KeySetVaultClient.class);
            when(keySetClient.getByMintId(activeMint.getId().toString()))
                    .thenReturn(Set.of(activeKeySet, archivedKeySet));
            factory.when(VaultClientFactory::keySetClient).thenReturn(keySetClient);

            // A keyset with no keys is enough: the generation is chosen before any key
            // material is read, so stubbing keys would only obscure what is under test.
            KeyVaultClient keyClient = mock(KeyVaultClient.class);
            when(keyClient.getKeysByKeySetId(anyString())).thenReturn(Set.of());
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(mock(KeyVault.class));

            assertThat(keySetIdsOf(DBMintVault.load(false)))
                    .containsExactly("active-keyset-id");
            assertThat(keySetIdsOf(DBMintVault.load(true)))
                    .containsExactly("archived-keyset-id");
        }
    }

    /**
     * An archived keyset stays reachable even when no mint is archived at all, which is the
     * exact staging shape: 3 mints, 0 archived, 3 of 4 keysets archived.
     */
    @Test
    void loadArchivedReturnsTheMintEvenWhenNoMintIsArchived() {
        MintEntity activeMint = new MintEntity();
        KeySetEntity archivedKeySet = keySetOf(activeMint, "archived-keyset-id", true);

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            @SuppressWarnings("unchecked")
            VaultClient<MintEntity> mintClient = mock(VaultClient.class);
            when(mintClient.retrieveAll()).thenReturn(List.of(activeMint));
            factory.when(() -> VaultClientFactory.getClient(MintEntity.class)).thenReturn(mintClient);

            KeySetVaultClient keySetClient = mock(KeySetVaultClient.class);
            when(keySetClient.getByMintId(activeMint.getId().toString()))
                    .thenReturn(Set.of(archivedKeySet));
            factory.when(VaultClientFactory::keySetClient).thenReturn(keySetClient);

            KeyVaultClient keyClient = mock(KeyVaultClient.class);
            when(keyClient.getKeysByKeySetId(anyString())).thenReturn(Set.of());
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(mock(KeyVault.class));

            assertThat(DBMintVault.load(true)).isNotEmpty();
            assertThat(keySetIdsOf(DBMintVault.load(true)))
                    .containsExactly("archived-keyset-id");
        }
    }

    private static KeySetEntity keySetOf(MintEntity mint, String keySetId, boolean archived) {
        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId(keySetId);
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySet.setArchived(archived);
        return keySet;
    }

    private static List<String> keySetIdsOf(List<Mint> mints) {
        return mints.stream()
                .flatMap(mint -> mint.getKeySets().stream())
                .map(KeySet::getId)
                .toList();
    }
}
