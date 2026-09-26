package xyz.tcheeric.cashu.vault.db.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The vault API must not answer an unauthenticated caller.
 *
 * <p>Before the 2026-09-05 audit (finding C-2) this service had no authentication at all: no
 * security dependency, no filter, no interceptor. {@code GET /vault/proof} returned every stored
 * proof, and a proof's {@code secret} plus {@code C} plus {@code witness} is spendable ecash.
 * {@code DELETE /vault/proof/{id}} let an anonymous caller destroy the double-spend record, and
 * {@code GET /vault/key} enumerated the storage path of every mint signing key. The service
 * binds all interfaces and the compose files published its port.
 *
 * <p>These tests pin the boundary on the endpoints that leak or destroy value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Vault API requires authentication")
class VaultApiAuthenticationTest {

    /** Matches vault.api.token in src/test/resources/application.properties. */
    private static final String VALID_TOKEN = "test-vault-api-token";

    /** Matches vault.api.read-token in src/test/resources/application.properties. */
    private static final String READ_TOKEN = "test-vault-read-token";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("without a credential")
    class Anonymous {

        @Test
        @DisplayName("listing every proof is rejected")
        void listProofsRejected() throws Exception {
            mockMvc.perform(get("/vault/proof"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("looking a proof up by secret is rejected")
        void retrieveBySecretRejected() throws Exception {
            mockMvc.perform(get("/vault/proof/secret/deadbeef"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("listing keys is rejected")
        void listKeysRejected() throws Exception {
            mockMvc.perform(get("/vault/key"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("deleting a proof is rejected")
        void deleteProofRejected() throws Exception {
            mockMvc.perform(delete("/vault/proof/123e4567-e89b-12d3-a456-426614174000"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("storing a key is rejected")
        void storeKeyRejected() throws Exception {
            mockMvc.perform(post("/vault/key")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("committing a hold as spent is rejected")
        void commitSpentRejected() throws Exception {
            mockMvc.perform(post("/vault/proof/hold/some-hold/commit-spent"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("with a wrong credential")
    class WrongCredential {

        @Test
        @DisplayName("a bad bearer token is rejected")
        void badTokenRejected() throws Exception {
            mockMvc.perform(get("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not-the-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token that is a prefix of the real one is rejected")
        void prefixTokenRejected() throws Exception {
            mockMvc.perform(get("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + VALID_TOKEN.substring(0, VALID_TOKEN.length() - 1)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("basic auth is not accepted in place of a bearer token")
        void basicAuthRejected() throws Exception {
            mockMvc.perform(get("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("with the configured credential")
    class Authenticated {

        @Test
        @DisplayName("the mint can list proofs")
        void listProofsAllowed() throws Exception {
            mockMvc.perform(get("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("the mint can list keys")
        void listKeysAllowed() throws Exception {
            mockMvc.perform(get("/vault/key")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("with the read-only credential (cashu-vault#154)")
    class ReadOnlyCredential {

        // The read token can list proofs, which is what monitoring needs.
        @Test
        @DisplayName("can read proofs")
        void canRead() throws Exception {
            mockMvc.perform(get("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + READ_TOKEN))
                    .andExpect(status().isOk());
        }

        // The read token cannot insert a proof.
        @Test
        @DisplayName("cannot store a proof")
        void cannotStore() throws Exception {
            mockMvc.perform(post("/vault/proof")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + READ_TOKEN)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        // The read token cannot change proof state, neither by marking spent nor by refunding.
        @Test
        @DisplayName("cannot change proof state")
        void cannotChangeProofState() throws Exception {
            mockMvc.perform(post("/vault/proof/mint/123e4567-e89b-12d3-a456-426614174000/mark-spent")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + READ_TOKEN)
                            .contentType("application/json")
                            .content("[\"y\"]"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/vault/proof/hold/some-hold/refund")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + READ_TOKEN))
                    .andExpect(status().isForbidden());
        }

        // The read token cannot delete anything.
        @Test
        @DisplayName("cannot delete")
        void cannotDelete() throws Exception {
            mockMvc.perform(delete("/vault/mint/123e4567-e89b-12d3-a456-426614174000")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + READ_TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("proof deletion (cashu-vault#154)")
    class ProofDeletion {

        // Even the mint's own credential cannot delete a proof: the endpoint no longer exists.
        @Test
        @DisplayName("is refused even with the write credential")
        void refusedWithWriteCredential() throws Exception {
            mockMvc.perform(delete("/vault/proof/123e4567-e89b-12d3-a456-426614174000")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("probes stay reachable")
    class Probes {

        @Test
        @DisplayName("health is anonymous so containers can probe it")
        void healthAnonymous() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }
}
