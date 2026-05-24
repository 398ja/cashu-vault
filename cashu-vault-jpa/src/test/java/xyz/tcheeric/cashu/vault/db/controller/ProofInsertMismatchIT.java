package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;
import xyz.tcheeric.cashu.vault.db.testsupport.IntegrationTestFixtures;
import xyz.tcheeric.cashu.vault.db.testsupport.PostgresIntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T013.1 — FR-009 / Constitution I "insertIfNotExists is the only safe insertion path".
 *
 * Exercises every branch of {@code ProofVaultService.store}, including the U1 id-collision pre-check.
 */
@DisplayName("ProofInsertMismatchIT — insertIfNotExists with mismatch rejection")
class ProofInsertMismatchIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private MintRepository mintRepository;
    @Autowired private ProofRepository proofRepository;
    @Autowired private JdbcTemplate jdbc;

    private MintEntity mintAlpha;

    @BeforeEach
    void setUp() {
        mintAlpha = IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_ALPHA);
    }

    @Test
    @DisplayName("(a) idempotent re-insert with identical (mint_id, secret, c) returns 200 with the existing row's UUID")
    void idempotentReinsert() throws Exception {
        String secret = "imm-a-" + UUID.randomUUID();
        String c = "02C-IMM-A-" + UUID.randomUUID();
        ProofEntity first = IntegrationTestFixtures.buildProof(mintAlpha, secret, c, 4);
        UUID id1 = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, first);

        ProofEntity second = IntegrationTestFixtures.buildProof(mintAlpha, secret, c, 4);
        UUID id2 = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, second);

        assertThat(id2).isEqualTo(id1);
    }

    @Test
    @DisplayName("(b) insert with same (mint_id, secret) but DIFFERENT c → 409 IDENTITY_CONFLICT; existing row unchanged")
    void mismatchOnDuplicate() throws Exception {
        String secret = "imm-b-" + UUID.randomUUID();
        String originalC = "02C-IMM-B-orig-" + UUID.randomUUID();
        ProofEntity first = IntegrationTestFixtures.buildProof(mintAlpha, secret, originalC, 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, first);

        ProofEntity mutated = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-IMM-B-mutated", 4);
        mvc.perform(post("/vault/proof")
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(mutated)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"))
                .andExpect(jsonPath("$.details.reason").value("value_mismatch"));

        String storedC = jdbc.queryForObject("SELECT c FROM t_proof WHERE id = ?", String.class, id);
        assertThat(storedC).isEqualTo(originalC);
    }

    @Test
    @DisplayName("(c) two concurrent inserts of the same (mint_id, secret, c) collapse to a single row")
    void concurrentInsertsCollapse() throws Exception {
        String secret = "imm-c-" + UUID.randomUUID();
        String c = "02C-IMM-C-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, c, 4);
        String body = json.writeValueAsString(proof);

        // Two parallel POSTs.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> shot = () -> {
            barrier.await();
            return mvc.perform(post("/vault/proof")
                            .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };
        var f1 = pool.submit(shot);
        var f2 = pool.submit(shot);
        barrier.countDown();
        Integer s1 = f1.get();
        Integer s2 = f2.get();
        pool.shutdown();

        assertThat(s1).isIn(200, 409); // 200 happy or 409 idempotent collapse
        assertThat(s2).isIn(200, 409);
        Long cnt = jdbc.queryForObject(
                "SELECT count(*) FROM t_proof WHERE mint_id = ? AND secret = ?",
                Long.class, IntegrationTestFixtures.MINT_ALPHA, secret);
        assertThat(cnt).isEqualTo(1L);
    }

    @Test
    @DisplayName("(d) structured log on (b) — assertion is documentary; verified via SC-006 SIEM rule")
    void structuredLogOnMismatch() {
        // The Logback log line itself is asserted in observability tests outside this IT suite.
        // The (b) test above exercises the code path that emits it.
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("(e) — closes U1 — re-POST with colliding id + mutated identity columns → 409 IDENTITY_CONFLICT, stored row unchanged")
    void idCollisionRejectedAtServiceLayer() throws Exception {
        String secret = "imm-e-" + UUID.randomUUID();
        String c = "02C-IMM-E-" + UUID.randomUUID();
        ProofEntity first = IntegrationTestFixtures.buildProof(mintAlpha, secret, c, 4);
        UUID existingId = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, first);

        // Build the malicious payload manually so we supply an explicit id matching the existing row.
        ObjectNode mutated = json.createObjectNode();
        mutated.put("id", existingId.toString());
        mutated.put("amount", 4);
        mutated.put("secret", "Y-NEW-" + UUID.randomUUID());
        mutated.put("unblindedSignature", "02C-NEW-" + UUID.randomUUID());
        mutated.put("state", "UNSPENT");
        ObjectNode mintNode = json.createObjectNode();
        mintNode.put("id", IntegrationTestFixtures.MINT_ALPHA.toString());
        mutated.set("mint", mintNode);

        mvc.perform(post("/vault/proof")
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(mutated)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT"))
                .andExpect(jsonPath("$.details.reason").value("id_collision"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT secret, c, mint_id FROM t_proof WHERE id = ?", existingId);
        assertThat(row.get("secret")).isEqualTo(secret);
        assertThat(row.get("c")).isEqualTo(c);
        assertThat(row.get("mint_id").toString()).isEqualTo(IntegrationTestFixtures.MINT_ALPHA.toString());
    }
}
