package xyz.tcheeric.cashu.vault.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A mint may hold many archived keysets per unit, and at most one active one.
 *
 * <p>The original constraint was {@code UNIQUE (unit, mint_id)}, which allowed a
 * mint exactly one keyset per unit ever and so made rotation impossible: the
 * replacement could not be inserted while the keyset it replaced still existed.
 * Deleting the old one to free the slot would strand every token it signed, as
 * NUT-02 archived keysets must go on redeeming.
 */
@SpringBootTest
class KeysetRotationConstraintIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // Ensures a rotated keyset can be inserted while the keyset it replaces is
    // still present — the case the old constraint made impossible.
    @Test
    void allowsARotatedKeysetAlongsideTheOneItReplaces() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final UUID mintId = insertMint(jdbc);

        insertKeyset(jdbc, mintId, "keyset-original", true);
        insertKeyset(jdbc, mintId, "keyset-rotated", false);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_keyset WHERE mint_id = ?", Integer.class, mintId))
            .as("both the retired and the replacement keyset must persist")
            .isEqualTo(2);
    }

    // Ensures many keysets may be retired over a mint's lifetime, so rotation can
    // happen repeatedly without ever discarding key material.
    @Test
    void allowsManyArchivedKeysetsForTheSameUnit() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final UUID mintId = insertMint(jdbc);

        insertKeyset(jdbc, mintId, "keyset-one", true);
        insertKeyset(jdbc, mintId, "keyset-two", true);
        insertKeyset(jdbc, mintId, "keyset-three", true);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_keyset WHERE mint_id = ? AND archived = true",
                Integer.class, mintId))
            .isEqualTo(3);
    }

    // Ensures the invariant that remains: exactly one keyset signs for a unit, so
    // there is never ambiguity about which one is active.
    @Test
    void refusesASecondActiveKeysetForTheSameUnit() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        final UUID mintId = insertMint(jdbc);

        insertKeyset(jdbc, mintId, "keyset-active", false);

        assertThatThrownBy(() -> insertKeyset(jdbc, mintId, "keyset-second-active", false))
            .as("a mint must never have two keysets signing for one unit")
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private UUID insertMint(final JdbcTemplate jdbc) {
        final UUID mintId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_mint (id, archived, created_at, updated_at, version) "
                + "VALUES (?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", mintId);
        return mintId;
    }

    private void insertKeyset(final JdbcTemplate jdbc, final UUID mintId,
                              final String keySetId, final boolean archived) {
        jdbc.update("INSERT INTO t_keyset (id, archived, created_at, updated_at, version, "
                + "key_set_id, unit, mint_id) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, ?, ?, ?)",
                UUID.randomUUID(), archived, keySetId, "sat", mintId);
    }
}
