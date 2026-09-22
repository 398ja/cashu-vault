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
}
