package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.testsupport.IntegrationTestFixtures;
import xyz.tcheeric.cashu.vault.db.testsupport.PostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T031 — US3 acceptance scenarios for admin audit timeline (FR-013).
 */
@DisplayName("ProofAuditTimelineIT — US3 admin audit timeline")
class ProofAuditTimelineIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private MintRepository mintRepository;

    private MintEntity mintAlpha;

    @BeforeEach
    void setUp() {
        mintAlpha = IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_ALPHA);
    }

    @Test
    @DisplayName("Admin happy path — UNSPENT → SPENT → tombstoned produces 3 revisions with principalId")
    void adminTimelineHappyPath() throws Exception {
        String secret = "audit-happy-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AUD-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SPENT\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OP-99\",\"force\":false}"))
                .andExpect(status().isOk());

        var res = mvc.perform(get("/vault/proof/mint/{m}/secret/{s}/audit",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mintId").value(IntegrationTestFixtures.MINT_ALPHA.toString()))
                .andExpect(jsonPath("$.current.state").value("SPENT"))
                .andExpect(jsonPath("$.current.tombstonedBy").value("admin"))
                .andExpect(jsonPath("$.revisions").isArray())
                .andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        JsonNode revs = body.get("revisions");
        assertThat(revs.size()).as("expected at least 3 revisions: insert, state, tombstone").isGreaterThanOrEqualTo(3);
        // First revision principal = mint-alpha; last = admin.
        assertThat(revs.get(0).get("principalId").asText()).isEqualTo("mint-alpha");
        assertThat(revs.get(revs.size() - 1).get("principalId").asText()).isEqualTo("admin");
        assertThat(revs.get(revs.size() - 1).get("tombstonedAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("Non-admin caller is rejected with 403")
    void nonAdminRejected() throws Exception {
        String secret = "audit-noadmin-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-NA-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}/audit",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unknown (mintId, secret) returns 404 NOT_FOUND")
    void unknownPairReturnsNotFound() throws Exception {
        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}/audit",
                        IntegrationTestFixtures.MINT_ALPHA, "no-such-secret")
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS)))
                .andExpect(status().isNotFound());
    }
}
