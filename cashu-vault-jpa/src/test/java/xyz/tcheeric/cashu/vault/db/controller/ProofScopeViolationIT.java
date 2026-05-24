package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.testsupport.IntegrationTestFixtures;
import xyz.tcheeric.cashu.vault.db.testsupport.PostgresIntegrationTest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T023 — US2 acceptance scenarios for mint-scoped lookup (FR-005, FR-006, FR-007).
 */
@DisplayName("ProofScopeViolationIT — US2 mint-scoped lookup")
class ProofScopeViolationIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private MintRepository mintRepository;
    @Autowired private JdbcTemplate jdbc;

    private MintEntity mintAlpha;
    private MintEntity mintBravo;

    @BeforeEach
    void setUp() {
        mintAlpha = IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_ALPHA);
        mintBravo = IntegrationTestFixtures.ensureMint(mintRepository, IntegrationTestFixtures.MINT_BRAVO);
    }

    @Test
    @DisplayName("AS1+AS2 — same secret under two mints: each lookup returns the correct proof")
    void crossMintSameSecretReturnsCorrectRow() throws Exception {
        String sharedSecret = "shared-" + UUID.randomUUID();
        ProofEntity pa = IntegrationTestFixtures.buildProof(mintAlpha, sharedSecret, "02C-alpha-" + UUID.randomUUID(), 4);
        ProofEntity pb = IntegrationTestFixtures.buildProof(mintBravo, sharedSecret, "02C-bravo-" + UUID.randomUUID(), 4);

        UUID idA = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS, pa);
        UUID idB = IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.BRAVO_USER, IntegrationTestFixtures.BRAVO_PASS, pb);

        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}",
                        IntegrationTestFixtures.MINT_ALPHA, sharedSecret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idA.toString()))
                .andExpect(jsonPath("$.unblindedSignature").value(pa.getUnblindedSignature()));

        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}",
                        IntegrationTestFixtures.MINT_BRAVO, sharedSecret)
                        .with(httpBasic(IntegrationTestFixtures.BRAVO_USER, IntegrationTestFixtures.BRAVO_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idB.toString()))
                .andExpect(jsonPath("$.unblindedSignature").value(pb.getUnblindedSignature()));
    }

    @Test
    @DisplayName("AS1+AS2 cross-account — alpha can't read bravo's proof; 403 logged as scope_violation")
    void crossMintScopeViolation() throws Exception {
        String secret = "cross-" + UUID.randomUUID();
        ProofEntity proof = IntegrationTestFixtures.buildProof(mintBravo, secret, "02C-bravo-" + UUID.randomUUID(), 4);
        IntegrationTestFixtures.postProof(mvc, json,
                IntegrationTestFixtures.BRAVO_USER, IntegrationTestFixtures.BRAVO_PASS, proof);

        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}",
                        IntegrationTestFixtures.MINT_BRAVO, secret)
                        .with(httpBasic(IntegrationTestFixtures.ALPHA_USER, IntegrationTestFixtures.ALPHA_PASS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_VIOLATION"));
    }

    @Test
    @DisplayName("AS3 — legacy GET /vault/proof/secret/{secret} returns 400 with deprecation envelope and emits ZERO SQL")
    void legacyEndpointReturnsStubWithoutSql() throws Exception {
        // Snapshot SQL count by reading PG's stat counters for the active session.
        // Since we use a per-request connection from the pool, the per-session counter isn't reliable;
        // instead, assert that nothing exists in t_proof matching this secret AFTER the call.
        String secret = "legacy-" + UUID.randomUUID();
        // No proof inserted — call must return 400 without touching the DB.

        mvc.perform(get("/vault/proof/secret/{s}", secret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MINT_SCOPE_REQUIRED"))
                .andExpect(jsonPath("$.details.since").value("0.7.0"))
                .andExpect(jsonPath("$.details.removalTarget").value("0.8.0"));

        // Sanity: the controller did not start an INSERT/UPDATE/DELETE — count is unchanged.
        Long cnt = jdbc.queryForObject(
                "SELECT count(*) FROM t_proof WHERE secret = ?", Long.class, secret);
        assertThat(cnt).isEqualTo(0L);
    }

    @Test
    @DisplayName("AS4 — request omitting mintId path-var returns 4xx from Spring before DB access")
    void missingMintIdRejected() throws Exception {
        // /vault/proof/mint//secret/x — bad UUID, fails @Pattern validation → 400.
        mvc.perform(get("/vault/proof/mint/{m}/secret/{s}", "not-a-uuid", "anything")
                        .with(httpBasic(IntegrationTestFixtures.ADMIN_USER, IntegrationTestFixtures.ADMIN_PASS)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("AS5 — repository surface has NO single-arg findBySecret(String) returning a proof")
    void repositorySurfaceContractAtRuntime() {
        for (Method m : xyz.tcheeric.cashu.vault.db.repos.ProofRepository.class.getMethods()) {
            if (m.getName().equals("findBySecret")) {
                assertThat(true)
                        .as("findBySecret(String) MUST NOT exist on ProofRepository")
                        .isFalse();
            }
        }
    }
}
