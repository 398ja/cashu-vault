package xyz.tcheeric.cashu.vault.hashi.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HCKeyVaultTest {

    @Mock
    private VaultClient<KeyEntity> dbClient;

    @Mock
    private HashiVaultClient hashiClient;

    @Mock
    private KeySetVaultClient keySetVaultClient;

    private HCKeyVault hcKeyVault;

    private MintEntity mint;
    private KeySetEntity keySet;

    @BeforeEach
    void setUp() {
        hcKeyVault = new HCKeyVault(dbClient, hashiClient);

        mint = new MintEntity();
        keySet = new KeySetEntity();
        keySet.setKeySetId("ks-001");
        keySet.setMint(mint);
    }

    @Test
    void store_writesSecretToVaultAndClearsPrivateKey() throws CashuErrorException {
        KeyEntity key = createKeyEntity("secret-key-value", BigInteger.ONE);
        when(hashiClient.storeSecret(anyString(), any())).thenReturn("cashu/keys/" + mint.getId() + "/ks-001/1");
        when(dbClient.store(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keySetClient).thenReturn(keySetVaultClient);
            when(keySetVaultClient.getByKeySetId("ks-001")).thenReturn(keySet);

            KeyEntity stored = hcKeyVault.store(key);

            verify(hashiClient).storeSecret(
                    eq("keys/" + mint.getId() + "/ks-001/1"),
                    any());
            assertThat(stored.getPrivateKey()).isNull();
            assertThat(stored.getVaultPath()).isEqualTo("cashu/keys/" + mint.getId() + "/ks-001/1");
            verify(dbClient).store(key);
        }
    }

    @Test
    void store_includesPrivateKeyAndCreatedAtInVaultData() throws CashuErrorException {
        KeyEntity key = createKeyEntity("my-private-key", BigInteger.valueOf(64));
        when(hashiClient.storeSecret(anyString(), any())).thenReturn("cashu/keys/path");
        when(dbClient.store(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keySetClient).thenReturn(keySetVaultClient);
            when(keySetVaultClient.getByKeySetId("ks-001")).thenReturn(keySet);

            hcKeyVault.store(key);

            @SuppressWarnings("unchecked")
            var dataCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(hashiClient).storeSecret(anyString(), dataCaptor.capture());
            Map<String, Object> capturedData = dataCaptor.getValue();
            assertThat(capturedData).containsKey("private_key");
            assertThat(capturedData.get("private_key")).isEqualTo("my-private-key");
            assertThat(capturedData).containsKey("created_at");
        }
    }

    @Test
    void retrieve_enrichesEntityWithVaultSecret() throws CashuErrorException {
        KeyEntity key = createKeyEntity(null, BigInteger.ONE);
        key.setVaultPath("cashu/keys/path");
        when(dbClient.retrieve("key-id")).thenReturn(key);
        when(hashiClient.getSecret("cashu/keys/path")).thenReturn(Map.of("private_key", "enriched-key"));

        KeyEntity result = hcKeyVault.retrieve("key-id");

        assertThat(result.getPrivateKey()).isEqualTo("enriched-key");
        verify(hashiClient).getSecret("cashu/keys/path");
    }

    @Test
    void retrieve_throwsWhenEntityNotFound() {
        when(dbClient.retrieve("missing-id")).thenReturn(null);

        assertThatThrownBy(() -> hcKeyVault.retrieve("missing-id"))
                .isInstanceOf(CashuErrorException.class)
                .hasMessage("Key not found");
    }

    @Test
    void retrieve_leavesPrivateKeyNullWhenVaultReturnsNull() throws CashuErrorException {
        KeyEntity key = createKeyEntity(null, BigInteger.ONE);
        key.setVaultPath("cashu/keys/missing-secret");
        when(dbClient.retrieve("key-id")).thenReturn(key);
        when(hashiClient.getSecret("cashu/keys/missing-secret")).thenReturn(null);

        KeyEntity result = hcKeyVault.retrieve("key-id");

        assertThat(result.getPrivateKey()).isNull();
    }

    @Test
    void retrieveByAmount_returnsMatchingKeyEnrichedWithSecret() throws CashuErrorException {
        KeyEntity key1 = createKeyEntity(null, BigInteger.ONE);
        key1.setVaultPath("cashu/keys/path/1");
        KeyEntity key2 = createKeyEntity(null, BigInteger.valueOf(2));
        key2.setVaultPath("cashu/keys/path/2");

        KeyVaultClient keyVaultClient = mock(KeyVaultClient.class);
        when(keyVaultClient.getKeysByKeySetId("ks-001")).thenReturn(Set.of(key1, key2));
        when(hashiClient.getSecret("cashu/keys/path/2")).thenReturn(Map.of("private_key", "key-for-2"));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyVaultClient);

            KeyEntity result = hcKeyVault.retrieveByAmount(BigInteger.valueOf(2), "ks-001");

            assertThat(result.getAmount()).isEqualTo(BigInteger.valueOf(2));
            assertThat(result.getPrivateKey()).isEqualTo("key-for-2");
        }
    }

    @Test
    void retrieveByAmount_throwsWhenNoMatchingAmount() {
        KeyEntity key = createKeyEntity(null, BigInteger.ONE);
        key.setVaultPath("cashu/keys/path/1");

        KeyVaultClient keyVaultClient = mock(KeyVaultClient.class);
        when(keyVaultClient.getKeysByKeySetId("ks-001")).thenReturn(Set.of(key));

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyVaultClient);

            assertThatThrownBy(() -> hcKeyVault.retrieveByAmount(BigInteger.valueOf(999), "ks-001"))
                    .isInstanceOf(CashuErrorException.class)
                    .hasMessageContaining("Key not found for amount: 999");
        }
    }

    @Test
    void store_throwsWhenPrivateKeyIsNull() {
        KeyEntity key = createKeyEntity(null, BigInteger.ONE);

        assertThatThrownBy(() -> hcKeyVault.store(key))
                .isInstanceOf(CashuErrorException.class)
                .hasMessage("Private key must not be null");
    }

    @Test
    void retrieve_handlesNullVaultPathGracefully() throws CashuErrorException {
        KeyEntity key = createKeyEntity(null, BigInteger.ONE);
        key.setVaultPath(null);
        when(dbClient.retrieve("key-id")).thenReturn(key);

        KeyEntity result = hcKeyVault.retrieve("key-id");

        assertThat(result.getPrivateKey()).isNull();
        verifyNoInteractions(hashiClient);
    }

    @Test
    void archive_delegatesToDbClient() throws CashuErrorException {
        KeyEntity archived = createKeyEntity(null, BigInteger.ONE);
        archived.setArchived(true);
        when(dbClient.archive("key-id")).thenReturn(archived);

        KeyEntity result = hcKeyVault.archive("key-id");

        verify(dbClient).archive("key-id");
        assertThat(result.isArchived()).isTrue();
    }

    @Test
    void delete_delegatesToDbClient() throws CashuErrorException {
        hcKeyVault.delete("key-id");

        verify(dbClient).delete("key-id");
    }

    private KeyEntity createKeyEntity(String privateKey, BigInteger amount) {
        KeyEntity key = new KeyEntity();
        key.setAmount(amount);
        key.setPrivateKey(privateKey);
        key.setKeySet(keySet);
        return key;
    }
}
