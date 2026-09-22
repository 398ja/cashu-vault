package xyz.tcheeric.cashu.vault.db.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// The vault API now requires authentication (audit C-2). These tests are about controller
// behaviour, so they run as an authenticated client; VaultApiAuthenticationTest covers the
// boundary itself.
@org.springframework.security.test.context.support.WithMockUser(roles = "VAULT_CLIENT")
@Transactional
class KeySetVaultControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void retrieveMissingKeySetReturnsNotFound() throws Exception {
        mockMvc.perform(get("/vault/keyset/" + UUID.randomUUID()))
                .andExpect(status().is2xxSuccessful());
    }

    /**
     * A NUT-02 v2 keyset id must be accepted by the lookup endpoints.
     * <p>
     * V8 widened key_set_id to VARCHAR(66) precisely so v2 ids could be stored,
     * but the controller kept a {@code @Size(max = 16)} sized for v1. The
     * mismatch was invisible in isolation and vicious in practice: looking up an
     * existing v2 keyset returned 400, callers that treat any error as
     * "not found" concluded it needed seeding, and the seeding attempt failed in
     * turn. Asserting "not 400" rather than a specific success status keeps this
     * about the validation boundary, which is what regressed — whether a
     * particular id happens to exist is a different question.
     */
    @Test
    void acceptsNut02V2KeySetId() throws Exception {
        final String v2 = "01" + "a".repeat(64);
        mockMvc.perform(get("/vault/keyset/id/" + v2))
                .andExpect(result -> assertNotEquals(400, result.getResponse().getStatus(),
                        "v2/v1 keyset ids must not be rejected by request validation"));
    }

    /**
     * v1 ids must keep working: archived keysets issued under them go on being
     * looked up, so widening support for v2 must not narrow support for v1.
     */
    @Test
    void stillAcceptsNut02V1KeySetId() throws Exception {
        mockMvc.perform(get("/vault/keyset/id/00e3372e61d05605"))
                .andExpect(result -> assertNotEquals(400, result.getResponse().getStatus(),
                        "v2/v1 keyset ids must not be rejected by request validation"));
    }

    /**
     * The replacement is a shape check, not merely a longer length cap, so input
     * that is the right length but not hex is still refused. The old
     * {@code @Size} constraint accepted anything up to its limit.
     */
    @Test
    void rejectsKeySetIdThatIsNotHex() throws Exception {
        mockMvc.perform(get("/vault/keyset/id/zzzzzzzzzzzzzzzz"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A v2-length id whose version byte is not {@code 01} is not a v2 id, and a
     * bare 66-character length cap would have let it through.
     */
    @Test
    void rejectsSixtySixCharIdWithWrongVersionByte() throws Exception {
        mockMvc.perform(get("/vault/keyset/id/02" + "a".repeat(64)))
                .andExpect(status().isBadRequest());
    }
}
