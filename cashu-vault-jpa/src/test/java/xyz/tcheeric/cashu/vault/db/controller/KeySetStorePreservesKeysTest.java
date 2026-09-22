package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.repos.KeyRepository;
import xyz.tcheeric.cashu.vault.db.repos.KeySetRepository;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Re-storing a key set must not destroy the keys it already has.
 *
 * <p>{@code KeySetEntity.keys} is {@code @OneToMany(orphanRemoval = true)} and also
 * {@code @JsonIgnore}, so a key set arriving over REST always deserialises with an empty
 * collection. Saving that entity as-is told Hibernate the set no longer had any keys, and
 * orphanRemoval deleted every one — so an idempotent re-store, which callers perform
 * expecting a no-op, silently destroyed the key material instead.
 *
 * <p>It happened for real: all 24 keys of a live staging keyset were deleted, leaving a mint
 * that advertised the keyset with zero keys and could not sign a single proof. Only the
 * database rows were lost, because the private keys themselves live in HashiCorp Vault.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "VAULT_CLIENT")
@Transactional
class KeySetStorePreservesKeysTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KeySetRepository keySetRepository;

    @Autowired
    private KeyRepository keyRepository;

    @Autowired
    private MintRepository mintRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reStoringAKeySetKeepsItsExistingKeys() throws Exception {
        final MintEntity mint = new MintEntity();
        mint.setId(UUID.randomUUID());
        mintRepository.save(mint);

        final KeySetEntity keySet = new KeySetEntity();
        keySet.setId(UUID.randomUUID());
        keySet.setKeySetId("01" + "b".repeat(64));
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySetRepository.save(keySet);

        // Two denominations is enough: the bug deleted every key regardless of how many.
        for (final int amount : new int[] {1, 2}) {
            final KeyEntity key = new KeyEntity();
            key.setId(UUID.randomUUID());
            key.setAmount(BigInteger.valueOf(amount));
            key.setVaultPath("cashu/keys/" + mint.getId() + "/" + keySet.getKeySetId() + "/" + amount);
            key.setKeySet(keySet);
            keyRepository.save(key);
        }
        assertEquals(2, keyRepository.findByKeySet_Id(keySet.getId()).orElseThrow().size(),
                "precondition: the key set starts with two keys");

        // Detach everything before the request. Without this the test shares one
        // persistence context with the controller, so the KeySetEntity it saves is still
        // the managed instance WITH its keys attached — and the bug cannot reproduce no
        // matter what the JSON payload contains. Production has no such shared context.
        entityManager.flush();
        entityManager.clear();

        // Exactly what a client sends: the same key set, serialised. `keys` is @JsonIgnore,
        // so the payload cannot carry them and the server must not read that as "none".
        mockMvc.perform(post("/vault/keyset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keySet)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals(2, keyRepository.findByKeySet_Id(keySet.getId()).orElseThrow().size(),
                "re-storing a key set must not delete its keys — orphanRemoval on a "
                        + "@JsonIgnore collection wipes them when the payload looks empty");
    }

    /**
     * The same question asked of the archive endpoint, which was not touched by the fix.
     *
     * <p>Archive loads the entity, flips a flag and saves it. That looks like the shape that
     * caused the data loss, so it is worth an explicit test rather than an assumption: if it
     * ever stops loading the managed instance — or someone "optimises" it into a detached
     * write — orphanRemoval would delete the keys of every keyset being retired, which is
     * exactly when the key material still matters for auditing already-issued proofs.
     */
    @Test
    void archivingAKeySetKeepsItsKeys() throws Exception {
        final MintEntity mint = new MintEntity();
        mint.setId(UUID.randomUUID());
        mintRepository.save(mint);

        final KeySetEntity keySet = new KeySetEntity();
        keySet.setId(UUID.randomUUID());
        keySet.setKeySetId("01" + "c".repeat(64));
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySetRepository.save(keySet);

        for (final int amount : new int[] {1, 2}) {
            final KeyEntity key = new KeyEntity();
            key.setId(UUID.randomUUID());
            key.setAmount(BigInteger.valueOf(amount));
            key.setVaultPath("cashu/keys/" + mint.getId() + "/" + keySet.getKeySetId() + "/" + amount);
            key.setKeySet(keySet);
            keyRepository.save(key);
        }

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/vault/keyset/archive/" + keySet.getId()))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals(2, keyRepository.findByKeySet_Id(keySet.getId()).orElseThrow().size(),
                "archiving a key set must not delete its keys: an archived keyset still has "
                        + "to verify proofs that were issued under it");
    }

    /**
     * A store payload that omits {@code archived} must not resurrect a retired key set.
     *
     * <p>{@code archived} is a plain boolean on {@code BaseEntity} defaulting to false, and
     * the store endpoint saves whatever it is handed. So a client that round-trips the entity
     * sends the flag and is fine, but one that posts a partial payload — an older client, a
     * hand-written call, anything that does not know the field exists — deserialises to
     * {@code archived = false} and un-archives the key set.
     *
     * <p>That is not cosmetic. ADR 0004 (cashu-mint) makes archived mean "refuses to sign",
     * and it is the mechanism behind keyset rotation and mint retirement. Silently clearing
     * it puts a retired keyset back into service.
     */
    @Test
    void storingWithoutTheArchivedFlagDoesNotUnarchiveAKeySet() throws Exception {
        final MintEntity mint = new MintEntity();
        mint.setId(UUID.randomUUID());
        mintRepository.save(mint);

        final KeySetEntity keySet = new KeySetEntity();
        keySet.setId(UUID.randomUUID());
        keySet.setKeySetId("01" + "d".repeat(64));
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySet.setArchived(true);
        keySetRepository.save(keySet);

        entityManager.flush();
        entityManager.clear();

        // A payload with no "archived" field at all, which is what any client written
        // against an older schema sends.
        final String payload = """
                {"id":"%s","keySetId":"%s","unit":"sat","mint":{"id":"%s"}}
                """.formatted(keySet.getId(), keySet.getKeySetId(), mint.getId());

        mockMvc.perform(post("/vault/keyset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals(true, keySetRepository.findById(keySet.getId()).orElseThrow().isArchived(),
                "a store that does not mention `archived` must not un-archive the key set: "
                        + "archived means 'refuses to sign' (ADR 0004), so clearing it puts a "
                        + "retired keyset back into service");
    }

    /**
     * The guard above is asymmetric, and this is the half that must keep working: archiving
     * BY storing {@code archived: true} is still honoured. Without this test, making the
     * flag permanently immutable would pass every other assertion here.
     */
    @Test
    void storingWithArchivedTrueStillArchives() throws Exception {
        final MintEntity mint = new MintEntity();
        mint.setId(UUID.randomUUID());
        mintRepository.save(mint);

        final KeySetEntity keySet = new KeySetEntity();
        keySet.setId(UUID.randomUUID());
        keySet.setKeySetId("01" + "e".repeat(64));
        keySet.setUnit("sat");
        keySet.setMint(mint);
        keySetRepository.save(keySet);

        entityManager.flush();
        entityManager.clear();

        keySet.setArchived(true);
        mockMvc.perform(post("/vault/keyset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keySet)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals(true, keySetRepository.findById(keySet.getId()).orElseThrow().isArchived(),
                "archiving through store must still work: only clearing the flag is refused");
    }
}
