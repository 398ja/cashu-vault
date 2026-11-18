package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.cashu.vault.db.books.BooksMetrics;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cashu-vault#156: the books endpoint, keyset attribution on the write path, and the books
 * metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Books: keyset attribution and spent totals (cashu-vault#156)")
class BooksEndpointTest {

    private static final String WRITE = "Bearer test-vault-api-token";
    private static final String READ = "Bearer test-vault-read-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BooksMetrics booksMetrics;
    @Autowired private MeterRegistry meterRegistry;

    private UUID mintId;
    private UUID otherMintId;
    private String keyset;
    private String otherMintKeyset;

    @BeforeEach
    void seed() {
        mintId = insertMint();
        otherMintId = insertMint();
        keyset = "00" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        otherMintKeyset = "00" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        insertKeyset(mintId, keyset, "sat");
        insertKeyset(otherMintId, otherMintKeyset, "sat");
    }

    @Test
    @DisplayName("store persists the keyset id the mint sends")
    void storePersistsKeysetId() throws Exception {
        String secret = store(keyset, 8);

        assertThat(jdbc.queryForObject("SELECT keyset_id FROM t_proof WHERE secret = ?", String.class, secret))
                .isEqualTo(keyset);
    }

    @Test
    @DisplayName("store refuses a keyset that belongs to another mint")
    void storeRefusesOtherMintsKeyset() throws Exception {
        mockMvc.perform(post("/vault/proof").header(HttpHeaders.AUTHORIZATION, WRITE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proofJson(UUID.randomUUID().toString(), otherMintKeyset, 8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("store refuses a keyset the vault has never seen")
    void storeRefusesUnknownKeyset() throws Exception {
        mockMvc.perform(post("/vault/proof").header(HttpHeaders.AUTHORIZATION, WRITE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proofJson(UUID.randomUUID().toString(), "00ffffffffffffff", 8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("insert-or-claim persists the keyset id and refuses another mint's keyset")
    void insertOrClaimCarriesKeyset() throws Exception {
        String secret = UUID.randomUUID().toString();
        String hold = "swap-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/" + hold + "/insert-or-claim")
                        .header(HttpHeaders.AUTHORIZATION, WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("[" + proofJson(secret, keyset, 4) + "]"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT keyset_id FROM t_proof WHERE secret = ?", String.class, secret))
                .isEqualTo(keyset);

        String foreign = UUID.randomUUID().toString();
        mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/" + hold + "/insert-or-claim")
                        .header(HttpHeaders.AUTHORIZATION, WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("[" + proofJson(foreign, otherMintKeyset, 4) + "]"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_proof WHERE secret = ?", Integer.class, foreign))
                .isZero();
    }

    @Test
    @DisplayName("a spend time in the request body is ignored")
    void spentAtFromCallerIsIgnored() throws Exception {
        String secret = UUID.randomUUID().toString();
        String body = proofJson(secret, keyset, 2).replace("{", "{\"spent_at\":\"2020-01-01T00:00:00Z\",");
        mockMvc.perform(post("/vault/proof").header(HttpHeaders.AUTHORIZATION, WRITE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT spent_at FROM t_proof WHERE secret = ?", Timestamp.class, secret))
                .isNull();
    }

    @Test
    @DisplayName("mark-spent records a spend time")
    void markSpentSetsSpentAt() throws Exception {
        String secret = store(keyset, 2);
        markSpent(secret);
        assertThat(jdbc.queryForObject("SELECT spent_at FROM t_proof WHERE secret = ?", Timestamp.class, secret))
                .isNotNull();
    }

    @Test
    @DisplayName("books sums spent value per keyset, amounts only, and is readable with the read token")
    void booksSumsSpentPerKeyset() throws Exception {
        markSpent(store(keyset, 8));
        markSpent(store(keyset, 2));
        store(keyset, 64); // unspent: not in the books
        insertLegacySpent(mintId, 16);

        JsonNode books = books(READ, Instant.now().plusMillis(1).plusSeconds(0));

        assertThat(books.get("spent")).hasSize(1);
        JsonNode row = books.get("spent").get(0);
        assertThat(row.get("keysetId").asText()).isEqualTo(keyset);
        assertThat(row.get("unit").asText()).isEqualTo("sat");
        assertThat(row.get("amount").asLong()).isEqualTo(10);
        assertThat(row.get("count").asLong()).isEqualTo(2);
        assertThat(books.get("unattributedSpent").get("amount").asLong()).isEqualTo(16);
        assertThat(books.get("unattributedSpent").get("count").asLong()).isEqualTo(1);
        String raw = books.toString();
        assertThat(raw).doesNotContain("secret").doesNotContain("\"C\"").doesNotContain("witness")
                .doesNotContain("hold_id");
    }

    @Test
    @DisplayName("books reports held value per keyset and hold kind")
    void booksReportsPending() throws Exception {
        String hold = "swap-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/" + hold + "/insert-or-claim")
                        .header(HttpHeaders.AUTHORIZATION, WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("[" + proofJson(UUID.randomUUID().toString(), keyset, 4) + ","
                                + proofJson(UUID.randomUUID().toString(), keyset, 1) + "]"))
                .andExpect(status().isOk());

        JsonNode pending = books(READ, Instant.now().minusSeconds(1)).get("pending");

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("keysetId").asText()).isEqualTo(keyset);
        assertThat(pending.get(0).get("holdKind").asText()).isEqualTo("SWAP");
        assertThat(pending.get(0).get("amount").asLong()).isEqualTo(5);
        assertThat(pending.get(0).get("holds").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same until returns the same spent totals before and after new spends")
    void booksIsReproducible() throws Exception {
        markSpent(store(keyset, 8));
        Thread.sleep(20);
        Instant until = Instant.now();
        Thread.sleep(20);
        JsonNode before = books(READ, until);

        markSpent(store(keyset, 32));
        JsonNode after = books(READ, until);

        assertThat(after.get("spent")).isEqualTo(before.get("spent"));
        assertThat(after.get("spent").get(0).get("amount").asLong()).isEqualTo(8);
    }

    @Test
    @DisplayName("the write token can read the books too")
    void writeTokenReadsBooks() throws Exception {
        books(WRITE, Instant.now().minusSeconds(1));
    }

    @Test
    @DisplayName("books refuses an until in the future, a missing one and a malformed one")
    void booksRefusesBadUntil() throws Exception {
        mockMvc.perform(get("/vault/mint/" + mintId + "/books")
                        .param("until", Instant.now().plusSeconds(60).toString())
                        .header(HttpHeaders.AUTHORIZATION, READ))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/vault/mint/" + mintId + "/books").header(HttpHeaders.AUTHORIZATION, READ))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/vault/mint/" + mintId + "/books").param("until", "yesterday")
                        .header(HttpHeaders.AUTHORIZATION, READ))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("books without a credential is rejected")
    void booksNeedsCredential() throws Exception {
        mockMvc.perform(get("/vault/mint/" + mintId + "/books")
                        .param("until", Instant.now().minusSeconds(1).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("books of an unknown mint is 404")
    void booksOfUnknownMint() throws Exception {
        mockMvc.perform(get("/vault/mint/" + UUID.randomUUID() + "/books")
                        .param("until", Instant.now().minusSeconds(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, READ))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("metrics report spent and pending value per mint and keyset")
    void metricsReportSpentAndPending() throws Exception {
        markSpent(store(keyset, 8));
        String hold = "melt-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/vault/proof/mint/" + mintId + "/hold/" + hold + "/insert-or-claim")
                        .header(HttpHeaders.AUTHORIZATION, WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("[" + proofJson(UUID.randomUUID().toString(), keyset, 2) + "]"))
                .andExpect(status().isOk());

        booksMetrics.refresh();

        assertThat(meterRegistry.get("cashu_vault_spent_amount")
                .tags("mint", mintId.toString(), "keyset", keyset, "unit", "sat")
                .functionCounter().count()).isEqualTo(8.0);
        assertThat(meterRegistry.get("cashu_vault_pending_amount")
                .tags("mint", mintId.toString(), "keyset", keyset, "unit", "sat")
                .gauge().value()).isEqualTo(2.0);

        mockMvc.perform(post("/vault/proof/hold/" + hold + "/commit-spent")
                        .header(HttpHeaders.AUTHORIZATION, WRITE))
                .andExpect(status().isOk());
        booksMetrics.refresh();

        assertThat(meterRegistry.get("cashu_vault_spent_amount")
                .tags("mint", mintId.toString(), "keyset", keyset, "unit", "sat")
                .functionCounter().count()).isEqualTo(10.0);
        assertThat(meterRegistry.get("cashu_vault_pending_amount")
                .tags("mint", mintId.toString(), "keyset", keyset, "unit", "sat")
                .gauge().value()).isZero();
    }

    private JsonNode books(String token, Instant until) throws Exception {
        String body = mockMvc.perform(get("/vault/mint/" + mintId + "/books")
                        .param("until", until.truncatedTo(ChronoUnit.MILLIS).toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String store(String keysetId, int amount) throws Exception {
        String secret = UUID.randomUUID().toString();
        mockMvc.perform(post("/vault/proof").header(HttpHeaders.AUTHORIZATION, WRITE)
                        .contentType(MediaType.APPLICATION_JSON).content(proofJson(secret, keysetId, amount)))
                .andExpect(status().isOk());
        return secret;
    }

    private void markSpent(String secret) throws Exception {
        mockMvc.perform(post("/vault/proof/mint/" + mintId + "/mark-spent")
                        .header(HttpHeaders.AUTHORIZATION, WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secret + "\"]"))
                .andExpect(status().isOk());
    }

    private String proofJson(String secret, String keysetId, int amount) {
        return "{\"mint\":{\"id\":\"" + mintId + "\"},\"amount\":" + amount + ",\"secret\":\"" + secret
                + "\",\"unblindedSignature\":\"c-" + secret + "\",\"keyset_id\":\"" + keysetId + "\"}";
    }

    private UUID insertMint() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO t_mint (id, archived, created_at, updated_at, version) VALUES (?, false, ?, ?, 0)",
                id, now, now);
        return id;
    }

    private void insertKeyset(UUID mint, String keysetId, String unit) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO t_keyset (id, archived, created_at, updated_at, version, key_set_id, unit, "
                        + "mint_id, input_fee_ppk) VALUES (?, false, ?, ?, 0, ?, ?, ?, 0)",
                UUID.randomUUID(), now, now, keysetId, unit, mint);
    }

    /** A row spent before V14: no keyset id and no spend time. */
    private void insertLegacySpent(UUID mint, int amount) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_proof (id, archived, created_at, updated_at, version, mint_id, amount, "
                        + "secret, c, state) VALUES (?, false, ?, ?, 0, ?, ?, ?, ?, 'SPENT')",
                id, now, now, mint, amount, "legacy-" + id, "c-" + id);
    }
}
