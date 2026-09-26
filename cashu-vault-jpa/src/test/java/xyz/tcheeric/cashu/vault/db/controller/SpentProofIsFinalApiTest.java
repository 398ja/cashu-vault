package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cashu-vault#154: a spent proof cannot be erased or rewritten through the API.
 *
 * <p>The vault is the mint's only record of spent proofs. Before this change {@code DELETE
 * /vault/proof/{id}} removed any row, SPENT included, and {@code POST /vault/proof} saved the
 * caller's entity as-is, which JPA turns into a merge when the body names an existing id. Either
 * made a spent proof spendable again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "VAULT_CLIENT")
@Transactional
@DisplayName("Spent proof is final through the API (cashu-vault#154)")
class SpentProofIsFinalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProofRepository proofRepository;

    private String mintId;

    @BeforeEach
    void createMint() throws Exception {
        String response = mockMvc.perform(post("/vault/mint")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mintId = objectMapper.readTree(response).get("id").asText();
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        // Deleting a spent proof is refused and the row survives, still SPENT.
        @Test
        @DisplayName("DELETE of a SPENT proof is a 4xx and the row survives")
        void deleteOfSpentProofIsRefused() throws Exception {
            JsonNode stored = storeProof(uniqueSecret(), "UNSPENT");
            String secret = stored.get("secret").asText();
            markSpent(secret).andExpect(content().string("1"));

            mockMvc.perform(delete("/vault/proof/" + stored.get("id").asText()))
                    .andExpect(status().isMethodNotAllowed());

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_SPENT);
        }

        // No proof can be deleted through the API, whatever its state.
        @Test
        @DisplayName("DELETE of an UNSPENT proof is a 4xx too")
        void deleteOfUnspentProofIsRefused() throws Exception {
            JsonNode stored = storeProof(uniqueSecret(), "UNSPENT");

            mockMvc.perform(delete("/vault/proof/" + stored.get("id").asText()))
                    .andExpect(status().isMethodNotAllowed());

            assertThat(proofRepository.findById(UUID.fromString(stored.get("id").asText()))).isPresent();
        }
    }

    @Nested
    @DisplayName("store")
    class Store {

        // Re-posting an existing row's id with its state set back to UNSPENT is refused, and
        // the spent row is unchanged: the whole-entity overwrite no longer works.
        @Test
        @DisplayName("POST with an existing id is a 409 and the row is unchanged")
        void storeWithExistingIdDoesNotOverwrite() throws Exception {
            JsonNode stored = storeProof(uniqueSecret(), "UNSPENT");
            String secret = stored.get("secret").asText();
            markSpent(secret).andExpect(content().string("1"));

            String overwrite = String.format("""
                    {"id": "%s", "version": 0, "mint": {"id": "%s"}, "secret": "%s",
                     "unblindedSignature": "c-%s", "amount": 1, "state": "UNSPENT"}
                    """, stored.get("id").asText(), mintId, uniqueSecret(), UUID.randomUUID());
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON).content(overwrite))
                    .andExpect(status().isConflict());

            ProofEntity row = proofRepository.findById(UUID.fromString(stored.get("id").asText())).orElseThrow();
            assertThat(row.getState()).isEqualTo(ProofEntity.STATE_SPENT);
            assertThat(row.getSecret()).isEqualTo(secret);
        }

        // A caller cannot insert a proof directly as SPENT; SPENT is reached only by transition.
        @Test
        @DisplayName("POST with state SPENT is a 400")
        void storeRefusesSpentState() throws Exception {
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson(uniqueSecret(), "SPENT")))
                    .andExpect(status().isBadRequest());
        }

        // A caller cannot smuggle a hold or an archived flag in on insert: those stay
        // server-managed.
        @Test
        @DisplayName("POST ignores a caller-supplied hold and archived flag")
        void storeIgnoresServerManagedFields() throws Exception {
            String secret = uniqueSecret();
            String body = String.format("""
                    {"mint": {"id": "%s"}, "secret": "%s", "unblindedSignature": "c-%s",
                     "amount": 1, "state": "PENDING", "hold_id": "saga-smuggled", "hold_kind": "MELT",
                     "archived": true}
                    """, mintId, secret, UUID.randomUUID());
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            ProofEntity row = proofRepository.findByMint_IdAndSecret(UUID.fromString(mintId), secret).orElseThrow();
            assertThat(row.getHoldId()).isNull();
            assertThat(row.getHoldKind()).isNull();
            assertThat(row.isArchived()).isFalse();
        }

        // Inserting a PENDING proof still works, since the mint's storePending relies on it.
        @Test
        @DisplayName("POST with state PENDING inserts a PENDING row")
        void storeAcceptsPendingState() throws Exception {
            String secret = uniqueSecret();
            storeProof(secret, "PENDING");

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_PENDING);
        }
    }

    @Nested
    @DisplayName("mark-spent")
    class MarkSpent {

        // Marking an UNSPENT proof spent flips it and reports it as spent.
        @Test
        @DisplayName("moves UNSPENT to SPENT")
        void marksUnspentProofSpent() throws Exception {
            String secret = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();

            markSpent(secret).andExpect(content().string("1"));

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_SPENT);
        }

        // Marking a held proof spent flips it and clears the hold, whichever flow held it.
        @Test
        @DisplayName("moves a held PENDING proof to SPENT and clears the hold")
        void marksHeldProofSpentAndClearsHold() throws Exception {
            String secret = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/swap-h1/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content("[\"" + secret + "\"]"))
                    .andExpect(content().string("1"));

            markSpent(secret).andExpect(content().string("1"));

            ProofEntity row = proofRepository.findByMint_IdAndSecret(UUID.fromString(mintId), secret).orElseThrow();
            assertThat(row.getState()).isEqualTo(ProofEntity.STATE_SPENT);
            assertThat(row.getHoldId()).isNull();
            assertThat(row.getHoldKind()).isNull();
        }

        // A retry of mark-spent on an already spent proof is harmless and still reports it spent.
        @Test
        @DisplayName("is idempotent on an already SPENT proof")
        void isIdempotent() throws Exception {
            String secret = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();
            markSpent(secret).andExpect(content().string("1"));

            markSpent(secret).andExpect(content().string("1"));

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_SPENT);
        }

        // A proof the vault has never seen is not counted, so the caller can tell it must be
        // inserted first.
        @Test
        @DisplayName("does not count a proof the vault has never seen")
        void unknownProofIsNotCounted() throws Exception {
            String known = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();

            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/mark-spent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[\"" + known + "\",\"" + uniqueSecret() + "\"]"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));
        }

        // Mark-spent is scoped to the mint in the path, so one mint cannot spend another's proof.
        @Test
        @DisplayName("is scoped to the mint")
        void isScopedToTheMint() throws Exception {
            String secret = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();

            mockMvc.perform(post("/vault/proof/mint/" + UUID.randomUUID() + "/mark-spent")
                            .contentType(MediaType.APPLICATION_JSON).content("[\"" + secret + "\"]"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_UNSPENT);
        }

        // An empty or blank list of secrets is a malformed request.
        @Test
        @DisplayName("rejects an empty or blank list with 400")
        void rejectsEmptyOrBlank() throws Exception {
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/mark-spent")
                            .contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/mark-spent")
                            .contentType(MediaType.APPLICATION_JSON).content("[\" \"]"))
                    .andExpect(status().isBadRequest());
        }

        // A refund cannot move a spent proof back to UNSPENT, even under the hold it was held by.
        @Test
        @DisplayName("a refund after mark-spent leaves the proof SPENT")
        void refundDoesNotUndoMarkSpent() throws Exception {
            String secret = storeProof(uniqueSecret(), "UNSPENT").get("secret").asText();
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-r/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content("[\"" + secret + "\"]"))
                    .andExpect(content().string("1"));
            markSpent(secret).andExpect(content().string("1"));

            mockMvc.perform(post("/vault/proof/hold/saga-r/refund"))
                    .andExpect(content().string("0"));

            assertThat(stateOf(secret)).isEqualTo(ProofEntity.STATE_SPENT);
        }
    }

    private JsonNode storeProof(String secret, String state) throws Exception {
        String response = mockMvc.perform(post("/vault/proof")
                        .contentType(MediaType.APPLICATION_JSON).content(proofJson(secret, state)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private ResultActions markSpent(String secret) throws Exception {
        return mockMvc.perform(post("/vault/proof/mint/" + mintId + "/mark-spent")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"" + secret + "\"]"))
                .andExpect(status().isOk());
    }

    private String stateOf(String secret) {
        return proofRepository.findByMint_IdAndSecret(UUID.fromString(mintId), secret)
                .orElseThrow().getState();
    }

    private String proofJson(String secret, String state) {
        return String.format("""
                {"mint": {"id": "%s"}, "secret": "%s", "unblindedSignature": "c-%s",
                 "amount": 1, "state": "%s"}
                """, mintId, secret, UUID.randomUUID(), state);
    }

    private static String uniqueSecret() {
        return "secret-" + UUID.randomUUID();
    }
}
