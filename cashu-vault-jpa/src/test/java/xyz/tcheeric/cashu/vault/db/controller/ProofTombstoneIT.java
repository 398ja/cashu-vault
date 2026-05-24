package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T012 — US1 acceptance scenarios for tombstone (FR-001, FR-002, FR-003, FR-010, FR-012).
 */
@DisplayName("ProofTombstoneIT — US1 acceptance scenarios")
class ProofTombstoneIT extends PostgresIntegrationTest {

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
    @DisplayName("AS1 — non-admin call to tombstone is rejected with 403 before any DB write")
    void nonAdminTombstoneRejected() throws Exception {
        String secret = "tomb-as1-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS1-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"audit\",\"force\":false}"))
                .andExpect(status().isForbidden());

        ProofEntity reloaded = proofRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTombstonedAt()).isNull();
        assertThat(reloaded.getTombstonedBy()).isNull();
    }

    @Test
    @DisplayName("AS2 — admin tombstone of a SPENT proof: row remains, tombstone fields populated, Envers revision recorded")
    void adminTombstoneSpentProof() throws Exception {
        String secret = "tomb-as2-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS2-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        // Transition to SPENT first.
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/state",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"SPENT\"}"))
                .andExpect(status().isOk());

        // Admin tombstone (no force needed for SPENT).
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"GDPR OP-1234\",\"force\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tombstonedAt").exists())
                .andExpect(jsonPath("$.tombstonedBy").value("admin"));

        // Row physically present.
        Long cnt = jdbc.queryForObject("SELECT count(*) FROM t_proof WHERE id = ?", Long.class, id);
        assertThat(cnt).isEqualTo(1L);

        // Live row has tombstoned_at populated.
        java.sql.Timestamp tsLive = jdbc.queryForObject(
                "SELECT tombstoned_at FROM t_proof WHERE id = ?", java.sql.Timestamp.class, id);
        assertThat(tsLive).as("live tombstoned_at must be set").isNotNull();

        // Envers audit table has at least one revision row for this id with tombstoned_at populated.
        Long auditTotal = jdbc.queryForObject(
                "SELECT count(*) FROM t_proof_a WHERE id = ?", Long.class, id);
        Long auditTomb = jdbc.queryForObject(
                "SELECT count(*) FROM t_proof_a WHERE id = ? AND tombstoned_at IS NOT NULL",
                Long.class, id);
        // Dump every revision row's state + tombstoned_at for diagnosis if assertion fails.
        var rows = jdbc.queryForList(
                "SELECT rev, revtype, state, tombstoned_at FROM t_proof_a WHERE id = ? ORDER BY rev",
                id);
        assertThat(auditTotal)
                .as("Envers revisions for id=%s: %s", id, rows)
                .isGreaterThanOrEqualTo(2L); // at least INSERT + tombstone
        assertThat(auditTomb)
                .as("Envers must capture the tombstone revision with tombstoned_at set; revisions=%s", rows)
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("AS3 — tombstoned SPENT proof is still SPENT on state-check; tombstoned fields visible")
    void tombstonedSpentStateCheck() throws Exception {
        String secret = "tomb-as3-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS3-" + UUID.randomUUID(), 4);
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
                        .content("{\"reason\":\"OP-1\",\"force\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SPENT"))
                .andExpect(jsonPath("$.tombstonedAt").exists())
                .andExpect(jsonPath("$.tombstonedBy").value("admin"));
    }

    @Test
    @DisplayName("AS4 — admin tombstone of UNSPENT without force → 400 TOMBSTONE_REQUIRES_FORCE; row unchanged")
    void unspentTombstoneRequiresForce() throws Exception {
        String secret = "tomb-as4-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS4-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"compliance\",\"force\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOMBSTONE_REQUIRES_FORCE"));

        ProofEntity reloaded = proofRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTombstonedAt()).isNull();
    }

    @Test
    @DisplayName("AS4b — admin tombstone of UNSPENT WITH force succeeds")
    void unspentTombstoneWithForce() throws Exception {
        String secret = "tomb-as4b-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS4b-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"compliance OP-2\",\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tombstonedAt").exists());
    }

    @Test
    @DisplayName("AS5 — re-tombstone returns 409 ALREADY_TOMBSTONED (idempotency)")
    void alreadyTombstoned() throws Exception {
        String secret = "tomb-as5-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-AS5-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);
        // Force-tombstone an UNSPENT proof for simplicity.
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OP-3\",\"force\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OP-3 again\",\"force\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TOMBSTONED"));
    }

    @Test
    @DisplayName("FR-012 strict — live row's tombstoned_at MUST come from DB now(), not JVM clock")
    void tombstonedAtIsDbClockOnLiveRow() throws Exception {
        String secret = "tomb-clock-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-CLK-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        // Bracket the tombstone call with two DB-clock samples. The live row's tombstoned_at
        // MUST fall inside this window — if it were JVM-clock-sourced it would only by chance
        // land inside this DB-derived bracket (and any meaningful skew between JVM and DB would
        // make the assertion fail).
        java.sql.Timestamp dbBefore = jdbc.queryForObject("SELECT now()", java.sql.Timestamp.class);
        mvc.perform(post("/vault/proof/mint/{m}/secret/{s}/tombstone",
                        IntegrationTestFixtures.MINT_ALPHA, secret)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"clock-check\",\"force\":true}"))
                .andExpect(status().isOk());
        java.sql.Timestamp dbAfter = jdbc.queryForObject("SELECT now()", java.sql.Timestamp.class);

        java.sql.Timestamp tombAt = jdbc.queryForObject(
                "SELECT tombstoned_at FROM t_proof WHERE id = ?", java.sql.Timestamp.class, id);

        assertThat(tombAt).as("tombstoned_at must be populated").isNotNull();
        assertThat(tombAt.toInstant())
                .as("tombstoned_at (%s) must fall in the DB-clock bracket [%s, %s] proving DB now() origin",
                        tombAt, dbBefore, dbAfter)
                .isBetween(dbBefore.toInstant(), dbAfter.toInstant());
    }

    @Test
    @DisplayName("FR-001 — DELETE /vault/proof/{id} is removed → 405 Method Not Allowed")
    void physicalDeleteIsRemoved() throws Exception {
        String secret = "tomb-del-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintAlpha, secret, "02C-DEL-" + UUID.randomUUID(), 4);
        UUID id = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, proof);

        mvc.perform(delete("/vault/proof/{id}", id)
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS)))
                .andExpect(status().isMethodNotAllowed());

        // Row still present.
        Long cnt = jdbc.queryForObject("SELECT count(*) FROM t_proof WHERE id = ?", Long.class, id);
        assertThat(cnt).isEqualTo(1L);
    }
}
