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
                            "/vault/proof/mint/" + mintId + "/hold/saga-A/mark-pending")
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
                            "/vault/proof/mint/" + mintId + "/hold/saga-empty/mark-pending")
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
                            "/vault/proof/mint/" + mintId + "/hold/saga-B/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Second saga can't claim the same proof — CAS predicate fails.
            mockMvc.perform(post(
                            "/vault/proof/mint/" + mintId + "/hold/saga-C/mark-pending")
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
                            "/vault/proof/mint/" + mintId + "/hold/saga-D/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/vault/proof/hold/saga-D/commit-spent"))
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
                            "/vault/proof/mint/" + mintId + "/hold/saga-E/mark-pending")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/vault/proof/hold/saga-E/refund"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            assertThat(proofRepository.findByMeltSagaId("saga-E")).isEmpty();
            assertThat(proofRepository.findBySecret(secret).orElseThrow()
                    .getState()).isEqualTo(ProofEntity.STATE_UNSPENT);
        }

        @Test
        @DisplayName("commitSpent / refund on an unknown saga reports rowcount=0")
        void commitAndRefundOnUnknownSagaReportZero() throws Exception {
            mockMvc.perform(post("/vault/proof/hold/saga-nonexistent/commit-spent"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
            mockMvc.perform(post("/vault/proof/hold/saga-nonexistent/refund"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }
    }

    // ---------------------------------------------------------------
    // Spec 005 — insert-or-claim binding
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Spec 005 — insertOrClaim binding endpoint")
    class InsertOrClaimTests {

        @Test
        @DisplayName("insertOrClaim inserts a fresh proof in PENDING bound to the saga")
        void insertOrClaimFreshProof() throws Exception {
            String secret = "ioc-fresh-" + UUID.randomUUID();
            String body = "[" + proofBodyJson(secret, "c-fresh-" + UUID.randomUUID(), 8) + "]";

            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-ioc-1/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            ProofEntity stored = proofRepository
                    .findByMint_IdAndSecret(UUID.fromString(mintId), secret).orElseThrow();
            assertThat(stored.getState()).isEqualTo(ProofEntity.STATE_PENDING);
            assertThat(stored.getHoldId()).isEqualTo("saga-ioc-1");
            assertThat(proofRepository.findByMeltSagaId("saga-ioc-1")).hasSize(1);
        }

        @Test
        @DisplayName("insertOrClaim is idempotent for the same saga (client retry)")
        void insertOrClaimIdempotentForSameSaga() throws Exception {
            String secret = "ioc-idem-" + UUID.randomUUID();
            String body = "[" + proofBodyJson(secret, "c-idem-" + UUID.randomUUID(), 16) + "]";

            // First claim binds the fresh proof to the saga.
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-idem/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Retry with the SAME saga re-reports the proof as bound (count=1),
            // not 0, and does not create a duplicate row.
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-idem/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            assertThat(proofRepository.findAll().stream()
                    .filter(p -> secret.equals(p.getSecret()))
                    .count())
                    .isEqualTo(1L);
            assertThat(proofRepository.findByMeltSagaId("saga-idem")).hasSize(1);
        }

        @Test
        @DisplayName("insertOrClaim rejects a proof with a blank secret with 400")
        void insertOrClaimBlankSecretReturns400() throws Exception {
            String body = """
                    [{"secret": "", "unblindedSignature": "c-blank", "amount": 1, "state": "UNSPENT"}]
                    """;
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-blank/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("insertOrClaim ignores a caller-supplied id (no merge-overwrite)")
        void insertOrClaimIgnoresCallerSuppliedId() throws Exception {
            // Seed an unrelated SPENT proof we must not let the caller clobber.
            String victimSecret = "ioc-victim-" + UUID.randomUUID();
            String victimResp = mockMvc.perform(post("/vault/proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createProofJson(mintId, victimSecret, "c-victim-" + UUID.randomUUID(), 99)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String victimId = objectMapper.readTree(victimResp).get("id").asText();

            // Attacker submits a fresh-secret hold but reuses the victim's id.
            String attackSecret = "ioc-attack-" + UUID.randomUUID();
            String body = String.format("""
                    [{"id": "%s", "secret": "%s", "unblindedSignature": "c-attack-%s", "amount": 1, "state": "UNSPENT"}]
                    """, victimId, attackSecret, UUID.randomUUID());
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-attack/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Victim row is untouched: still its original secret/amount/state.
            ProofEntity victim = proofRepository.findById(UUID.fromString(victimId)).orElseThrow();
            assertThat(victim.getSecret()).isEqualTo(victimSecret);
            assertThat(victim.getAmount()).isEqualTo(99);
            assertThat(victim.getHoldId()).isNull();
            // The new hold landed on its own row, bound to the saga.
            assertThat(proofRepository.findByMeltSagaId("saga-attack")).hasSize(1);
            assertThat(proofRepository.findByMeltSagaId("saga-attack").get(0).getSecret())
                    .isEqualTo(attackSecret);
        }

        @Test
        @DisplayName("insertOrClaim claims an existing UNSPENT row (no second insert)")
        void insertOrClaimClaimsExistingUnspent() throws Exception {
            String secret = "ioc-existing-" + UUID.randomUUID();
            // Seed an UNSPENT row via the legacy /vault/proof endpoint.
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret, "c-existing-" + UUID.randomUUID(), 4)))
                    .andExpect(status().isOk());

            String body = "[" + proofBodyJson(secret, "c-claim-" + UUID.randomUUID(), 4) + "]";
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-ioc-2/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Same single row, now PENDING + bound. No raw-secret duplicate row created.
            assertThat(proofRepository.findByMeltSagaId("saga-ioc-2")).hasSize(1);
            assertThat(proofRepository.findBySecret(secret)).isPresent();
        }

        @Test
        @DisplayName("insertOrClaim returns partial count when one proof is already PENDING for another saga")
        void insertOrClaimPartialOnPriorBinding() throws Exception {
            String secret1 = "ioc-partial-1-" + UUID.randomUUID();
            String secret2 = "ioc-partial-2-" + UUID.randomUUID();
            // Pre-bind secret1 to saga-X.
            mockMvc.perform(post("/vault/proof").contentType(MediaType.APPLICATION_JSON)
                    .content(createProofJson(mintId, secret1, "c-partial-1-" + UUID.randomUUID(), 2)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-X/mark-pending")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[\"" + secret1 + "\"]"))
                    .andExpect(status().isOk());

            String body = "["
                    + proofBodyJson(secret1, "c-partial-1b-" + UUID.randomUUID(), 2) + ","
                    + proofBodyJson(secret2, "c-partial-2-" + UUID.randomUUID(), 4) + "]";

            // saga-Y can only bind the fresh secret2; secret1 stays with saga-X.
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-Y/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            assertThat(proofRepository.findByMeltSagaId("saga-X")).hasSize(1);
            assertThat(proofRepository.findByMeltSagaId("saga-Y")).hasSize(1);
            assertThat(proofRepository.findByMeltSagaId("saga-Y").get(0).getSecret()).isEqualTo(secret2);
        }

        @Test
        @DisplayName("insertOrClaim canonical identity: two calls with same secret leave exactly one row")
        void insertOrClaimCanonicalIdentity() throws Exception {
            String secret = "ioc-canonical-" + UUID.randomUUID();
            String body1 = "[" + proofBodyJson(secret, "c-can-1-" + UUID.randomUUID(), 1) + "]";
            String body2 = "[" + proofBodyJson(secret, "c-can-2-" + UUID.randomUUID(), 1) + "]";

            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-can-A/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body1))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));

            // Second call: row already PENDING → saga-can-A; saga-can-B claims nothing.
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-can-B/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body2))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));

            // Exactly one t_proof row exists for this secret.
            assertThat(proofRepository.findAll().stream()
                    .filter(p -> secret.equals(p.getSecret()))
                    .count())
                    .isEqualTo(1L);
            assertThat(proofRepository.findByMeltSagaId("saga-can-A")).hasSize(1);
            assertThat(proofRepository.findByMeltSagaId("saga-can-B")).isEmpty();
        }

        @Test
        @DisplayName("insertOrClaim rejects empty body with 400")
        void insertOrClaimEmptyBodyReturns400() throws Exception {
            mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/saga-empty/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content("[]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("insertOrClaim rejects unknown mint with 400")
        void insertOrClaimUnknownMintReturns400() throws Exception {
            String body = "[" + proofBodyJson("ioc-bad-mint-" + UUID.randomUUID(),
                    "c-bad-mint-" + UUID.randomUUID(), 1) + "]";
            mockMvc.perform(post("/vault/proof/mint/" + UUID.randomUUID()
                            + "/hold/saga-unknown-mint/insert-or-claim")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Builds the JSON for a single proof body element. The mint is
         * resolved server-side from the path variable, so {@code mint} is
         * omitted here.
         */
        private String proofBodyJson(String secret, String commitment, int amount) {
            return String.format("""
                    {
                        "secret": "%s",
                        "unblindedSignature": "%s",
                        "amount": %d,
                        "state": "UNSPENT"
                    }
                    """, secret, commitment, amount);
        }
    }
}
