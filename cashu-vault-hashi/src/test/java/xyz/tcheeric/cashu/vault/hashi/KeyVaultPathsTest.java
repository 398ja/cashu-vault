package xyz.tcheeric.cashu.vault.hashi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A key row carries the KV path its private key lives at, and that row is writable through
 * {@code POST /vault/key}. So the read path is assembled from data a caller can influence, and it
 * must not be able to reach another key's material (audit M-13).
 *
 * <p>The first repair only checked that {@code keys/} appeared as a segment, which constrains
 * shape and not ownership: a path naming a different mint and keyset satisfied it. These tests are
 * mostly about that distinction.
 */
@DisplayName("Key vault path ownership")
class KeyVaultPathsTest {

    private static final UUID MINT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MINT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("a key's own path is accepted")
    void ownPathIsAccepted() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/" + MINT_ID + "/keyset-a/8", key)).isTrue();
    }

    @Test
    @DisplayName("a mount prefix is allowed")
    void mountPrefixedPathIsAccepted() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        // storeSecret returns the path prefixed with the engine mount, which varies by deployment.
        assertThat(KeyVaultPaths.isPathForEntity("secret/keys/" + MINT_ID + "/keyset-a/8", key))
                .isTrue();
    }

    /** The regression: shape-only validation accepted all of these. */
    @Test
    @DisplayName("another mint's key path is refused")
    void otherMintsPathIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/" + OTHER_MINT_ID + "/keyset-a/8", key))
                .as("one mint must not be able to read another mint's private keys")
                .isFalse();
    }

    @Test
    @DisplayName("another keyset's path is refused")
    void otherKeysetPathIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/" + MINT_ID + "/keyset-b/8", key)).isFalse();
    }

    @Test
    @DisplayName("another amount's path is refused")
    void otherAmountPathIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/" + MINT_ID + "/keyset-a/16", key))
                .isFalse();
    }

    @Test
    @DisplayName("a foreign mount naming another key is refused")
    void foreignMountWithOtherKeyIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        // The exact string the old shape check accepted.
        assertThat(KeyVaultPaths.isPathForEntity(
                "othermount/keys/" + OTHER_MINT_ID + "/keyset-b/1", key))
                .as("contains \"/keys/\", so shape-only validation passed it")
                .isFalse();
    }

    @Test
    @DisplayName("a segment that merely ends with the expected path is refused")
    void nonBoundarySuffixIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        // Suffix matching must be on a segment boundary, or "evilkeys/..." passes as "keys/...".
        assertThat(KeyVaultPaths.isPathForEntity("evilkeys/" + MINT_ID + "/keyset-a/8", key))
                .isFalse();
    }

    @Test
    @DisplayName("traversal and absolute paths are refused")
    void traversalIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/../../root", key)).isFalse();
        assertThat(KeyVaultPaths.isPathForEntity("/keys/" + MINT_ID + "/keyset-a/8", key)).isFalse();
        assertThat(KeyVaultPaths.isPathForEntity("keys\\" + MINT_ID, key)).isFalse();
    }

    /**
     * Encoded traversal was an open question under shape-based validation. Under equality it is
     * not interesting: an encoded path is simply not equal to the computed one.
     */
    @Test
    @DisplayName("percent-encoded traversal is refused")
    void encodedTraversalIsRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("keys/%2e%2e/%2e%2e/root", key)).isFalse();
        assertThat(KeyVaultPaths.isPathForEntity(
                "keys/" + MINT_ID + "/keyset-a/8/%2e%2e/%2e%2e/other", key)).isFalse();
    }

    @Test
    @DisplayName("null and blank are refused")
    void nullAndBlankAreRefused() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity(null, key)).isFalse();
        assertThat(KeyVaultPaths.isPathForEntity("   ", key)).isFalse();
        assertThat(KeyVaultPaths.isPathForEntity("keys/" + MINT_ID + "/keyset-a/8", null))
                .isFalse();
    }

    @Test
    @DisplayName("surrounding whitespace does not defeat the comparison")
    void whitespaceIsStripped() {
        KeyEntity key = key(MINT_ID, "keyset-a", 8);

        assertThat(KeyVaultPaths.isPathForEntity("  keys/" + MINT_ID + "/keyset-a/8  ", key))
                .isTrue();
    }

    private static KeyEntity key(UUID mintId, String keySetId, int amount) {
        MintEntity mint = new MintEntity();
        mint.setId(mintId);

        KeySetEntity keySet = new KeySetEntity();
        keySet.setKeySetId(keySetId);
        keySet.setMint(mint);

        KeyEntity key = new KeyEntity();
        key.setKeySet(keySet);
        key.setAmount(java.math.BigInteger.valueOf(amount));
        return key;
    }
}
