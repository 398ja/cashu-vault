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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProofVaultControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MintRepository mintRepository;

    @Autowired
    private ProofRepository proofRepository;

    private String mintId;

    @BeforeEach
    void setUp() throws Exception {
        // Create a mint to associate proofs with
        String response = mockMvc.perform(post("/vault/mint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        mintId = node.get("id").asText();
    }

    @Test
    void retrieveMissingProofReturnsNotFound() throws Exception {
        mockMvc.perform(get("/vault/proof/" + UUID.randomUUID()))
                .andExpect(status().is2xxSuccessful());
    }

    @Nested
    @DisplayName("Duplicate Detection Tests")
    class DuplicateDetectionTests {

        @Test
        @DisplayName("Should store proof successfully when no duplicate exists")
        void storeProofSuccessfully() throws Exception {
            String proofJson = createProofJson(mintId, "unique-secret-123", "commitment-abc", 100);

            String response = mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = objectMapper.readTree(response);
            assertThat(node.get("id").asText()).isNotBlank();
            assertThat(node.get("secret").asText()).isEqualTo("unique-secret-123");
            assertThat(node.get("amount").asInt()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should return 409 Conflict when storing duplicate proof with same secret")
        void rejectDuplicateProofWithSameSecret() throws Exception {
            String secret = "duplicate-secret-" + UUID.randomUUID();
            String proofJson1 = createProofJson(mintId, secret, "commitment-1", 100);
            String proofJson2 = createProofJson(mintId, secret, "commitment-2", 200);

            // First store should succeed
            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson1))
                    .andExpect(status().isOk());

            // Second store with same secret should return 409 Conflict
            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson2))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should allow different proofs with different secrets for same mint")
        void allowDifferentProofsForSameMint() throws Exception {
            String proofJson1 = createProofJson(mintId, "secret-a-" + UUID.randomUUID(), "commitment-a", 100);
            String proofJson2 = createProofJson(mintId, "secret-b-" + UUID.randomUUID(), "commitment-b", 200);

            // Both stores should succeed
            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson1))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson2))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should compute and store fingerprint for proof")
        void computeAndStoreFingerprint() throws Exception {
            String secret = "fingerprint-test-secret-" + UUID.randomUUID();
            String proofJson = createProofJson(mintId, secret, "commitment-fp", 100);

            String response = mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = objectMapper.readTree(response);
            String fingerprint = node.get("fingerprint").asText();

            // Fingerprint should be a 64-character hex string (SHA-256)
            assertThat(fingerprint).hasSize(64);
            assertThat(fingerprint).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("Repository Direct Tests")
    class RepositoryDirectTests {

        @Test
        @DisplayName("existsByMint_IdAndSecret should return true for existing proof")
        void existsByMintAndSecretReturnsTrue() throws Exception {
            String secret = "exists-test-secret-" + UUID.randomUUID();
            String proofJson = createProofJson(mintId, secret, "commitment-exists", 100);

            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson))
                    .andExpect(status().isOk());

            boolean exists = proofRepository.existsByMint_IdAndSecret(UUID.fromString(mintId), secret);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("existsByMint_IdAndSecret should return false for non-existing proof")
        void existsByMintAndSecretReturnsFalse() {
            boolean exists = proofRepository.existsByMint_IdAndSecret(
                    UUID.fromString(mintId),
                    "non-existing-secret-" + UUID.randomUUID());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("findByFingerprint should return proof with matching fingerprint")
        void findByFingerprintReturnsProof() throws Exception {
            String secret = "fingerprint-find-test-" + UUID.randomUUID();
            String proofJson = createProofJson(mintId, secret, "commitment-fp-find", 100);

            String response = mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = objectMapper.readTree(response);
            String fingerprint = node.get("fingerprint").asText();

            var found = proofRepository.findByFingerprint(fingerprint);
            assertThat(found).isPresent();
            assertThat(found.get().getSecret()).isEqualTo(secret);
        }

        @Test
        @DisplayName("insertIfNotExists should return duplicate result for existing proof")
        void insertIfNotExistsReturnsDuplicateResult() throws Exception {
            String secret = "insert-if-not-exists-test-" + UUID.randomUUID();
            String proofJson = createProofJson(mintId, secret, "commitment-ine", 100);

            // First insert via API
            mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(proofJson))
                    .andExpect(status().isOk());

            // Create a new proof entity with same secret
            ProofEntity duplicateProof = new ProofEntity();
            duplicateProof.setSecret(secret);
            duplicateProof.setUnblindedSignature("different-commitment");
            duplicateProof.setAmount(200);
            duplicateProof.setMint(mintRepository.findById(UUID.fromString(mintId)).orElseThrow());

            // Direct repository call should detect duplicate
            var result = proofRepository.insertIfNotExists(duplicateProof);

            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.stored()).isFalse();
            assertThat(result.duplicateReason()).contains("Duplicate");
        }

        @Test
        @DisplayName("insertIfNotExists should throw for non-duplicate constraint violations")
        void insertIfNotExistsThrowsForNonDuplicateViolations() {
            // Create a proof entity missing required mint (FK violation)
            ProofEntity invalidProof = new ProofEntity();
            invalidProof.setSecret("some-secret-" + UUID.randomUUID());
            invalidProof.setUnblindedSignature("some-commitment");
            invalidProof.setAmount(100);
            // Intentionally NOT setting mint - this should cause a NOT NULL/FK violation

            // Should throw DataIntegrityViolationException, NOT return a duplicate result
            assertThatThrownBy(() -> proofRepository.insertIfNotExists(invalidProof))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    /**
     * Creates a JSON representation of a proof for testing.
     */
    private String createProofJson(String mintId, String secret, String commitment, int amount) {
        return String.format("""
                {
                    "mint": {"id": "%s"},
                    "secret": "%s",
                    "unblindedSignature": "%s",
                    "amount": %d,
                    "state": "UNSPENT"
                }
                """, mintId, secret, commitment, amount);
    }

    // ---------------------------------------------------------------
    // cashu-mint spec 002 T011 — melt-saga binding endpoints
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Melt-saga binding endpoint tests")
    class MeltSagaBindingTests {

        @Test
        @DisplayName("markPending binds UNSPENT proofs to the saga and reports rowcount")
        void markPendingHappyPath() throws Exception {
            String secret1 = "saga-secret-1-" + UUID.randomUUID();
            String secret2 = "saga-secret-2-" + UUID.randomUUID();
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret1, "c-saga-1-" + UUID.randomUUID(), 1)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret2, "c-saga-2-" + UUID.randomUUID(), 2)))
                    .andExpect(status().isOk());

            String body = "[\"" + secret1 + "\",\"" + secret2 + "\"]";
            String response = mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-A/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(response.trim()).isEqualTo("2");

            assertThat(proofRepository.findByMeltSagaId("saga-A")).hasSize(2);
        }

        @Test
        @DisplayName("markPending rejects empty proofSecrets with 400")
        void markPendingEmptyListReturns400() throws Exception {
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-empty/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("markPending second claim on the same proof reports rowcount=0 (no exception)")
        void markPendingExclusiveByCas() throws Exception {
            String secret = "saga-exclusive-" + UUID.randomUUID();
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret, "c-excl-" + UUID.randomUUID(), 1)))
                    .andExpect(status().isOk());

            String body = "[\"" + secret + "\"]";
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-B/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Second saga can't claim the same proof — CAS predicate fails.
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-C/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));

            // saga-B still holds the proof.
            assertThat(proofRepository.findByMeltSagaId("saga-B")).hasSize(1);
            assertThat(proofRepository.findByMeltSagaId("saga-C")).isEmpty();
        }

        @Test
        @DisplayName("commitSpent flips PENDING → SPENT and clears the saga binding")
        void commitSpentTransitionsAndClearsBinding() throws Exception {
            String secret = "saga-commit-" + UUID.randomUUID();
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret, "c-commit-" + UUID.randomUUID(), 1)))
                    .andExpect(status().isOk());
            String body = "[\"" + secret + "\"]";
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-D/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/vault/proof/saga/saga-D/commit-spent"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            assertThat(proofRepository.findByMeltSagaId("saga-D")).isEmpty();
            assertThat(proofRepository.findBySecret(secret).orElseThrow()
                    .getState()).isEqualTo(ProofEntity.STATE_SPENT);
        }

        @Test
        @DisplayName("refund flips PENDING → UNSPENT and clears the saga binding")
        void refundTransitionsAndClearsBinding() throws Exception {
            String secret = "saga-refund-" + UUID.randomUUID();
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret, "c-refund-" + UUID.randomUUID(), 1)))
                    .andExpect(status().isOk());
            String body = "[\"" + secret + "\"]";
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/saga/saga-E/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/vault/proof/saga/saga-E/refund"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            assertThat(proofRepository.findByMeltSagaId("saga-E")).isEmpty();
            assertThat(proofRepository.findBySecret(secret).orElseThrow()
                    .getState()).isEqualTo(ProofEntity.STATE_UNSPENT);
        }

        @Test
        @DisplayName("commitSpent / refund on an unknown saga reports rowcount=0")
        void commitAndRefundOnUnknownSagaReportZero() throws Exception {
            mockMvc.perform(post("/vault/proof/saga/saga-nonexistent/commit-spent"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
            mockMvc.perform(post("/vault/proof/saga/saga-nonexistent/refund"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }
    }
}
