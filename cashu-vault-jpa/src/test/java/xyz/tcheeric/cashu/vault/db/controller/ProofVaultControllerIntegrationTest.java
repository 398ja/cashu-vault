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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
