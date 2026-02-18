package xyz.tcheeric.cashu.vault.hashi.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import xyz.tcheeric.cashu.vault.hashi.config.HashiVaultProperties;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class HashiVaultClientIT {

    private static final String VAULT_TOKEN = "test-root-token";

    @Container
    static VaultContainer<?> vault = new VaultContainer<>("hashicorp/vault:1.18")
            .withVaultToken(VAULT_TOKEN)
            .withSecretInVault("secret/testing", "top_secret=password123");

    private static HashiVaultClient hashiVaultClient;

    @BeforeAll
    static void setUp() throws Exception {
        // Enable KV v2 at "cashu" mount
        vault.execInContainer("vault", "secrets", "enable", "-path=cashu", "kv-v2");

        VaultEndpoint endpoint = VaultEndpoint.from(
                URI.create("http://" + vault.getHost() + ":" + vault.getFirstMappedPort()));
        VaultTemplate vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(VAULT_TOKEN));

        HashiVaultProperties properties = new HashiVaultProperties();
        properties.setUri("http://" + vault.getHost() + ":" + vault.getFirstMappedPort());
        properties.getEngine().setMount("cashu");

        hashiVaultClient = new HashiVaultClient(vaultTemplate, properties);
    }

    @Test
    void testStoreAndRetrieveSecret() {
        String path = "keys/test-mint/test-keyset/1";
        Map<String, Object> data = Map.of(
                "private_key", "abc123secretkey",
                "created_at", "2026-02-17T00:00:00Z"
        );

        String fullPath = hashiVaultClient.storeSecret(path, data);

        assertThat(fullPath).isEqualTo("cashu/" + path);

        Map<String, Object> retrieved = hashiVaultClient.getSecret(fullPath);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.get("private_key")).isEqualTo("abc123secretkey");
        assertThat(retrieved.get("created_at")).isEqualTo("2026-02-17T00:00:00Z");
    }

    @Test
    void testGetSecretReturnsNullForMissingPath() {
        Map<String, Object> result = hashiVaultClient.getSecret("cashu/nonexistent/path");
        assertThat(result).isNull();
    }

    @Test
    void testDeleteSecret() {
        String path = "keys/test-mint/test-keyset/delete-test";
        Map<String, Object> data = Map.of("private_key", "to-be-deleted");

        hashiVaultClient.storeSecret(path, data);
        hashiVaultClient.deleteSecret("cashu/" + path);

        Map<String, Object> retrieved = hashiVaultClient.getSecret("cashu/" + path);
        assertThat(retrieved).isNull();
    }

    @Test
    void testOverwriteSecret() {
        String path = "keys/test-mint/test-keyset/overwrite";
        Map<String, Object> data1 = Map.of("private_key", "original-key");
        Map<String, Object> data2 = Map.of("private_key", "updated-key");

        hashiVaultClient.storeSecret(path, data1);
        hashiVaultClient.storeSecret(path, data2);

        Map<String, Object> retrieved = hashiVaultClient.getSecret("cashu/" + path);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.get("private_key")).isEqualTo("updated-key");
    }

    @Test
    void testGetMountReturnsConfiguredMount() {
        assertThat(hashiVaultClient.getMount()).isEqualTo("cashu");
    }
}
