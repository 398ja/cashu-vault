package xyz.tcheeric.cashu.vault.db.controller;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T013.2 — FR-008 / Constitution I "no silent overwrite".
 *
 * Exercises the dedicated state-transition endpoint and verifies both
 * service-layer (T019.5) and DB-layer (T005.1 — updatable=false) backstops.
 */
@DisplayName("ProofStateTransitionIT — FR-008 silent-overwrite backstops")
class ProofStateTransitionIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private MintRepository mintRepository;
    @Autowired private ProofRepository proofRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MintEntity mintAlpha;

    @BeforeEach
    void setUp() {
        mintAlpha = IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_ALPHA);
    }

    @Test
    @DisplayName("(a) POST /state {to:PENDING} flips state to PENDING — Envers revision recorded with state change")
    void transitionToPending() throws Exception {
        String secret = "st-a-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-ST-A-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));

        String dbState = jdbc.queryForObject("SELECT state FROM t_proof WHERE id = ?", String.class, id);
        assertThat(dbState).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("(b) UNSPENT → SPENT and PENDING → SPENT both succeed")
    void transitionsToSpent() throws Exception {
        // UNSPENT → SPENT directly.
        String s1 = "st-b1-" + UUID.randomUUID();
        ProofEntity p1 = IntegrationTestFixtures.buildProof(mintAlpha, s1, "02C-B1-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, p1);
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, s1)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SPENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SPENT"));

        // UNSPENT → PENDING → SPENT.
        String s2 = "st-b2-" + UUID.randomUUID();
        ProofEntity p2 = IntegrationTestFixtures.buildProof(mintAlpha, s2, "02C-B2-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, p2);
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, s2)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"PENDING\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, s2)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SPENT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("(c1) service-layer mismatch guard: POST /vault/proof with existing (mint_id, secret) but different c → 409 IDENTITY_CONFLICT (value_mismatch)")
    void serviceMismatchGuard() throws Exception {
        String secret = "st-c1-" + UUID.randomUUID();
        String origC = "02C-orig-" + UUID.randomUUID();
        ProofEntity first = IntegrationTestFixtures.buildProof(mintAlpha, secret, origC, 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, first);

        ProofEntity mutated = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-mutated", 4);
        mvc.perform(post("/vault/proof")
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(mutated)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.reason").value("value_mismatch"));

        String storedC = jdbc.queryForObject("SELECT c FROM t_proof WHERE id = ?", String.class, id);
        assertThat(storedC).isEqualTo(origC);
    }

    @Test
    @DisplayName("(c2) service-layer id-collision guard: POST /vault/proof with colliding id + mutated columns → 409 IDENTITY_CONFLICT (id_collision)")
    void serviceIdCollisionGuard() throws Exception {
        String secret = "st-c2-" + UUID.randomUUID();
        String origC = "02C-c2-orig-" + UUID.randomUUID();
        ProofEntity first = IntegrationTestFixtures.buildProof(mintAlpha, secret, origC, 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, first);

        ObjectNode payload = json.createObjectNode();
        payload.put("id", id.toString());
        payload.put("amount", 4);
        payload.put("secret", "Y-NEW");
        payload.put("unblindedSignature", "02C-NEW");
        payload.put("state", "UNSPENT");
        ObjectNode mintNode = json.createObjectNode();
        mintNode.put("id", IntegrationTestFixtures.MINT_ALPHA.toString());
        payload.set("mint", mintNode);

        mvc.perform(post("/vault/proof")
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.reason").value("id_collision"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT secret, c FROM t_proof WHERE id = ?", id);
        assertThat(row.get("secret")).isEqualTo(secret);
        assertThat(row.get("c")).isEqualTo(origC);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("(c3) DB-layer backstop: direct saveAndFlush of a mutated managed entity leaves identity columns unchanged at the row level")
    void dbLayerImmutability() {
        String secret = "st-c3-" + UUID.randomUUID();
        String origC = "02C-c3-" + UUID.randomUUID();
        ProofEntity stored;
        // Insert via the service (within the test transaction).
        stored = IntegrationTestFixtures.buildProof(mintAlpha, secret, origC, 4);
        ProofRepository.InsertResult ins = proofRepository.insertIfNotExists(stored);
        assertThat(ins.isDuplicate()).isFalse();
        UUID id = ins.proof().getId();
        em.flush();
        em.clear();

        // Load fresh, mutate identity columns in memory, save.
        ProofEntity loaded = proofRepository.findById(id).orElseThrow();
        loaded.setSecret("Y-NEW");
        loaded.setUnblindedSignature("02C-NEW");
        proofRepository.saveAndFlush(loaded);

        // T005.1 updatable=false means Hibernate generated UPDATE that omitted secret/c/mint_id.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT secret, c FROM t_proof WHERE id = ?", id);
        assertThat(row.get("secret")).isEqualTo(secret);
        assertThat(row.get("c")).isEqualTo(origC);
    }

    @Test
    @DisplayName("(d) /state endpoint enforces mint-scope cross-check (403 on mismatch)")
    void stateEndpointEnforcesScope() throws Exception {
        // mint-bravo's account tries to transition a mint-alpha proof.
        IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_BRAVO);
        String secret = "st-d-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-D-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.BRAVO_USER, IntegrationTestFixtures.BRAVO_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SPENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_VIOLATION"));
    }
}
