package xyz.tcheeric.cashu.vault.db.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import xyz.tcheeric.cashu.vault.db.model.MintEntity;

/**
 * What {@link VaultClient#retrieveAll()} does with a real vault response body.
 */
class VaultClientRetrieveAllTest {

    private static final String BASE_URL = "http://vault.test";

    /**
     * The regression this test exists for. retrieveAll described its response as
     * {@code ParameterizedTypeReference<List<T>>}, but T is erased at that point, so
     * Jackson was asked for the abstract BaseEntity and threw
     * "cannot construct instance of BaseEntity (no Creators...)". Every caller of
     * DBMintVault.load(archive) died on it, which is every mint reading keysets from
     * the vault. The body below is what the running vault actually answers for
     * GET /vault/mint.
     */
    @Test
    @DisplayName("Retrieving all entities deserialises them as the client's own entity type")
    void retrievesAllAsTheConcreteEntityType() {
        final VaultClient<MintEntity> client = new VaultClient<>(MintEntity.class, BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(client.restTemplate).build();
        server.expect(requestTo(BASE_URL + "/vault/mint"))
            .andRespond(withSuccess("""
                [{"id":"1f240ace-0e4e-42dd-bdcb-9ad4ce8eaeae","archived":false,\
                "createdAt":"2026-08-27T23:17:41.622219Z",\
                "updatedAt":"2026-08-27T23:17:41.622224Z","version":1}]""",
                MediaType.APPLICATION_JSON));

        final List<MintEntity> mints = client.retrieveAll();

        assertThat(mints).singleElement().satisfies(mint ->
            assertThat(mint.getId()).isEqualTo(UUID.fromString("1f240ace-0e4e-42dd-bdcb-9ad4ce8eaeae")));
        server.verify();
    }

    /**
     * A vault holding nothing of this type is an empty list rather than a null the
     * caller has to defend against.
     */
    @Test
    @DisplayName("An empty vault is an empty list")
    void emptyVaultIsEmptyList() {
        final VaultClient<MintEntity> client = new VaultClient<>(MintEntity.class, BASE_URL);
        final MockRestServiceServer server = MockRestServiceServer.bindTo(client.restTemplate).build();
        server.expect(requestTo(BASE_URL + "/vault/mint"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.retrieveAll()).isEmpty();
        server.verify();
    }
}
