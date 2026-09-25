package xyz.tcheeric.cashu.vault.db.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;
import xyz.tcheeric.cashu.vault.db.repos.ProofRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Binding proofs to a hold must cost a bounded number of queries, independent of
 * how many proofs the mint has already issued.
 *
 * <p>{@code ProofEntity.mint} was {@code @ManyToOne(cascade = CascadeType.ALL)} and
 * {@code MintEntity.proofs} cascaded {@code PERSIST/MERGE} back, so saving one proof
 * reached the mint, which reached every proof the mint had ever issued. Hibernate
 * hydrated the whole collection once per inserted proof.
 *
 * <p>Measured on staging at 12,778 proof rows: one {@code insertOrClaim} binding four
 * fresh proofs ran 67 statements, four of them a full {@code SELECT ... WHERE mint_id=?}
 * at 20-29ms each, and took 877ms end to end. Only 100ms of that was SQL: the rest was
 * the JVM materialising ~51,000 entities and discarding them. Since the cost tracked
 * total proofs ever issued rather than swap size, it grew without bound as the mint was
 * used, and projected past the mint's 5s slow-call breaker at roughly 70,000 proofs.
 *
 * <p>These tests pin the shape of the fix rather than a millisecond figure: the entity
 * collection must not be loaded, and the query count must not grow with history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "VAULT_CLIENT")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@DisplayName("insertOrClaim cost does not grow with the mint's proof history")
class ProofHoldInsertCostTest {

    // t_proof has a (mint_id, c) uniqueness constraint and c is derived from the
    // signature, so every proof in these tests needs its own signature. Sharing one
    // makes the fixture collide on the second insert rather than testing anything.
    private static final String SIGNATURE_PREFIX = "02a9acd1e51c62e9c6d0e1e1dbbd0f4e0f0e0d0c0b0a0908070605040302";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MintRepository mintRepository;

    @Autowired
    private ProofRepository proofRepository;

    @Autowired
    private EntityManager entityManager;

    private MintEntity mint;

    private int signatureCounter;

    @BeforeEach
    void createMint() {
        mint = mintRepository.save(new MintEntity());
        entityManager.flush();
    }

    /**
     * Binding two new proofs must not load the mint's existing proofs. With the cascade
     * in place this loads all of them, so the assertion fails once history exists.
     */
    @Test
    @DisplayName("binding proofs does not hydrate the mint's existing proofs")
    void bindingProofsDoesNotLoadTheMintsProofCollection() throws Exception {
        givenExistingProofs(25);

        Statistics statistics = statistics();
        statistics.clear();

        insertOrClaim("saga-no-hydration", 2);

        assertThat(statistics.getEntityLoadCount())
                .as("entities loaded while binding 2 proofs, with 25 already issued: "
                        + "the mint's proof collection must not be hydrated")
                .isLessThanOrEqualTo(10);
    }

    /**
     * The real regression is growth. Binding the same number of proofs must cost the same
     * whether the mint has 0 or hundreds of existing proofs, so this measures the same
     * mint twice at very different history sizes rather than asserting an absolute count.
     *
     * <p>The two history sizes differ by 10x deliberately. With the cascade present the
     * statement count tracked history, so the gap grows with the multiplier and a
     * tolerance cannot hide it. A fixed allowance absorbs incidental per-call variation
     * (the hold lookup differs by one statement once rows exist) without absorbing
     * anything proportional.
     */
    @Test
    @DisplayName("query count does not grow as the mint's proof history grows 10x")
    void costDoesNotGrowWithProofHistory() throws Exception {
        Statistics statistics = statistics();

        givenExistingProofs(20);
        statistics.clear();
        insertOrClaim("saga-short-history", 2);
        long queriesWithShortHistory = statistics.getPrepareStatementCount();

        givenExistingProofs(180);
        statistics.clear();
        insertOrClaim("saga-long-history", 2);
        long queriesWithLongHistory = statistics.getPrepareStatementCount();

        assertThat(queriesWithLongHistory)
                .as("statements to bind 2 proofs: %d with 20 proofs of history, %d with 200. "
                                + "Binding cost must not track history",
                        queriesWithShortHistory, queriesWithLongHistory)
                .isLessThanOrEqualTo(queriesWithShortHistory + 2);
    }

    /**
     * Guards the fix itself: a cascade from proof to mint would let a proof write create
     * or modify mint rows, which is also what filled the audit table with one mint
     * revision per proof.
     */
    @Test
    @DisplayName("binding proofs does not write mint revisions")
    void bindingProofsDoesNotUpdateTheMint() throws Exception {
        givenExistingProofs(5);

        Statistics statistics = statistics();
        statistics.clear();

        insertOrClaim("saga-no-mint-write", 2);
        entityManager.flush();

        assertThat(statistics.getEntityUpdateCount() + statistics.getEntityInsertCount())
                .as("entity writes while binding 2 proofs: only the proof rows themselves")
                .isLessThanOrEqualTo(4);
    }

    /** Binding must still work: the rows land bound to the saga. */
    @Test
    @DisplayName("proofs are still bound to the hold")
    void proofsAreStillBoundToTheHold() throws Exception {
        String holdId = "saga-still-binds";

        insertOrClaim(holdId, 3);
        entityManager.flush();
        entityManager.clear();

        assertThat(proofRepository.findAll())
                .filteredOn(proof -> holdId.equals(proof.getHoldId()))
                .as("proofs bound to the hold")
                .hasSize(3);
    }

    private void givenExistingProofs(int count) {
        for (int i = 0; i < count; i++) {
            ProofEntity existing = new ProofEntity();
            existing.setMint(mint);
            existing.setAmount(2);
            existing.setSecret("existing-" + UUID.randomUUID());
            existing.setUnblindedSignature(uniqueSignature());
            existing.setState(ProofEntity.STATE_UNSPENT);
            proofRepository.save(existing);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void insertOrClaim(String holdId, int proofCount) throws Exception {
        List<ProofEntity> proofs = new ArrayList<>();
        for (int i = 0; i < proofCount; i++) {
            ProofEntity proof = new ProofEntity();
            proof.setAmount(1);
            proof.setSecret(holdId + "-secret-" + i + "-" + UUID.randomUUID());
            proof.setUnblindedSignature(uniqueSignature());
            proofs.add(proof);
        }

        mockMvc.perform(post("/vault/proof/mint/{mintId}/hold/{holdId}/insert-or-claim",
                        mint.getId(), holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proofs)))
                .andExpect(status().isOk());
    }

    /** A distinct 33-byte compressed point per proof, to keep (mint_id, c) unique. */
    private String uniqueSignature() {
        return SIGNATURE_PREFIX + String.format("%08x", signatureCounter++);
    }

    private Statistics statistics() {
        return entityManager.unwrap(SessionImplementor.class)
                .getFactory()
                .getStatistics();
    }
}
