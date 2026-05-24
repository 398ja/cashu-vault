package xyz.tcheeric.cashu.vault.db.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixture helpers for Testcontainers-based US1/US2/US3 ITs.
 */
public final class IntegrationTestFixtures {

    /** Mint UUID bound to the {@code mint-alpha} service account in application-it-pg.properties. */
    public static final UUID MINT_ALPHA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Mint UUID bound to the {@code mint-bravo} service account in application-it-pg.properties. */
    public static final UUID MINT_BRAVO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final String ADMIN_USER = "admin";
    public static final String ADMIN_PASS = "admin-test";
    public static final String ALPHA_USER = "mint-alpha";
    public static final String ALPHA_PASS = "alpha-test";
    public static final String BRAVO_USER = "mint-bravo";
    public static final String BRAVO_PASS = "bravo-test";

    private IntegrationTestFixtures() {
    }

    public static MintEntity ensureMint(MintRepository mintRepository, UUID id) {
        return mintRepository.findById(id).orElseGet(() -> {
            MintEntity m = new MintEntity();
            m.setId(id);
            return mintRepository.save(m);
        });
    }

    public static ProofEntity buildProof(MintEntity mint, String secret, String c, int amount) {
        ProofEntity p = new ProofEntity();
        p.setMint(mint);
        p.setSecret(secret);
        p.setUnblindedSignature(c);
        p.setAmount(amount);
        p.setState(ProofEntity.STATE_UNSPENT);
        return p;
    }

    /**
     * POSTs a fresh proof via the controller (admin auth, scoped by mint).
     * Returns the persisted entity id.
     */
    public static UUID postProof(MockMvc mvc, ObjectMapper json,
                                 String user, String pass,
                                 ProofEntity proof) throws Exception {
        String body = json.writeValueAsString(proof);
        var res = mvc.perform(post("/vault/proof")
                        .with(httpBasic(user, pass))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(json.readTree(res.getResponse().getContentAsString())
                .get("id").asText());
    }
}
