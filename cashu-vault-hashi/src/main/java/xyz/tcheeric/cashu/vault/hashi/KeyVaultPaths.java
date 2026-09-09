package xyz.tcheeric.cashu.vault.hashi;

import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

/**
 * Decides whether a stored vault path belongs to the key it is stored on.
 *
 * <h2>Why an ownership check and not a prefix check</h2>
 *
 * <p>A key row carries the KV path its private key lives at, and that row is writable through
 * {@code POST /vault/key}. Before the vault had authentication, anyone could point a key row at
 * any path in the mount and read the material back through the enrich-on-read path (audit M-13).
 * Authentication closed the front door, but a read path assembled from stored data should not be
 * able to reach another key's material regardless of who wrote the row.
 *
 * <p>The first repair checked that {@code keys/} appeared as a path segment. That constrains shape
 * and not ownership: {@code othermount/keys/<other mint>/<other keyset>/1} passes it, so one mint
 * could still read another mint's private keys. It also invited an unwinnable argument about
 * traversal spellings, {@code ..} against {@code %2e%2e} against overlong encodings.
 *
 * <p>This class asks the question that was answerable all along. Exactly one path is ever written
 * per key, so the only legitimate stored value is that path, and comparing against it makes
 * encoding tricks irrelevant: a path containing {@code ..} or {@code %2e%2e} is simply not equal
 * to the computed one.
 */
@Slf4j
public final class KeyVaultPaths {

    private KeyVaultPaths() {
    }

    /**
     * The single path a key's private material is written to.
     *
     * <p>{@code HashiVaultClient.storeSecret} returns this prefixed with the engine mount, so a
     * stored value may be either this or {@code <mount>/} + this.
     */
    public static String buildPath(final KeyEntity key) {
        return String.format("keys/%s/%s/%s",
                key.getKeySet().getMint().getId(),
                key.getKeySet().getKeySetId(),
                key.getAmount());
    }

    /**
     * Whether {@code path} is the path {@code entity} would be written to, allowing for a
     * deployment-specific engine mount prefix.
     *
     * @return false for anything else, including a path belonging to a different key
     */
    public static boolean isPathForEntity(final String path, final KeyEntity entity) {
        if (path == null || path.isBlank() || entity == null) {
            return false;
        }
        final String normalised = path.strip();
        if (normalised.startsWith("/") || normalised.contains("\\") || normalised.contains("..")) {
            return false;
        }
        final String expected;
        try {
            expected = buildPath(entity);
        } catch (RuntimeException e) {
            // A key whose own path cannot be computed cannot have a path validated against it.
            log.error("Cannot compute the expected vault path for key {}", entity.getId(), e);
            return false;
        }
        // Suffix match on a segment boundary, so "otherkeys/keys/..." cannot pass as "keys/...".
        return normalised.equals(expected) || normalised.endsWith("/" + expected);
    }
}
