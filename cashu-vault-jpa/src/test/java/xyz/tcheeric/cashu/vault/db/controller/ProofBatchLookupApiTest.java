package xyz.tcheeric.cashu.vault.db.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** {@code POST /vault/proof/mint/{mintId}/lookup}: one request for many proofs of a mint. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "VAULT_CLIENT")
@Transactional
@DisplayName("Batch proof lookup")
class ProofBatchLookupApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private String mintId;

  @BeforeEach
  void createMint() throws Exception {
    mintId = newMint();
  }

  // Recorded secrets come back with their state, unknown ones are simply absent.
  @Test
  @DisplayName("returns the recorded rows and omits unknown secrets")
  void returnsRecordedRowsOnly() throws Exception {
    String unspent = uniqueSecret();
    String spent = uniqueSecret();
    store(mintId, unspent, "UNSPENT");
    store(mintId, spent, "SPENT");

    lookup(mintId, List.of(unspent, spent, uniqueSecret()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].secret", Matchers.containsInAnyOrder(unspent, spent)))
        .andExpect(jsonPath("$[?(@.secret=='" + spent + "')].state").value("SPENT"));
  }

  // The lookup is scoped: another mint's row for the same secret is never returned.
  @Test
  @DisplayName("does not return another mint's row")
  void scopedToTheMint() throws Exception {
    String secret = uniqueSecret();
    store(newMint(), secret, "SPENT");

    lookup(mintId, List.of(secret)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  // Empty, blank and oversized batches are malformed requests.
  @Test
  @DisplayName("rejects empty, blank and oversized batches")
  void rejectsMalformedBatches() throws Exception {
    lookup(mintId, List.of()).andExpect(status().isBadRequest());
    lookup(mintId, List.of(" ")).andExpect(status().isBadRequest());
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i <= ProofVaultController.MAX_LOOKUP_SECRETS; i++) {
      tooMany.add("s" + i);
    }
    lookup(mintId, tooMany).andExpect(status().isBadRequest());
  }

  private org.springframework.test.web.servlet.ResultActions lookup(String mint, List<String> secrets)
      throws Exception {
    return mockMvc.perform(
        post("/vault/proof/mint/" + mint + "/lookup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(secrets)));
  }

  private String newMint() throws Exception {
    String response =
        mockMvc
            .perform(post("/vault/mint").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("id").asText();
  }

  private void store(String mint, String secret, String state) throws Exception {
    // POST /vault/proof refuses a SPENT row (cashu-vault#154), so spent ones go through mark-spent.
    storeUnspent(mint, secret);
    if ("SPENT".equals(state)) {
      mockMvc
          .perform(
              post("/vault/proof/mint/" + mint + "/mark-spent")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("[\"" + secret + "\"]"))
          .andExpect(status().isOk());
    }
  }

  private void storeUnspent(String mint, String secret) throws Exception {
    mockMvc
        .perform(
            post("/vault/proof")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                        {"mint": {"id": "%s"}, "secret": "%s", "unblindedSignature": "c-%s",
                         "amount": 1, "state": "%s"}
                        """,
                        mint, secret, UUID.randomUUID(), "UNSPENT")))
        .andExpect(status().isOk());
  }

  private static String uniqueSecret() {
    return "secret-" + UUID.randomUUID();
  }
}
