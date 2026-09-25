package xyz.tcheeric.cashu.vault.api;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

/**
 * Derives the published public key of a signing key, in one place.
 *
 * <p>The derivation itself is a single library call. It lives here because three unrelated
 * places need the same answer and have to agree on it: the two {@code store} paths that stamp
 * {@link KeyEntity#getPublicKey()} when a key is first written, and the load path that falls
 * back to deriving for rows written before the column existed (issue #146). A keyset whose
 * stored public key disagreed with the derived one would advertise denominations the mint
 * cannot sign for, which is worse than the round trips this replaced.
 */
@Slf4j
public final class KeyPublicKeys {

    private KeyPublicKeys() {
    }

    /**
     * The compressed secp256k1 public key for a private key in hex.
     *
     * <p>Returned as hex rather than as a {@link PublicKey} because both callers store it, and
     * the hex form is what NUT-01 publishes.
     */
    public static String deriveFrom(@NonNull final String privateKeyHex) {
        return PrivateKey.derivePublicKey(PrivateKey.fromString(privateKeyHex)).toString();
    }

    /**
     * Records the derived public key on a key that is about to be stored.
     *
     * <p>Does nothing when the key already carries one, so a caller that derived it earlier is
     * not second-guessed, and nothing when there is no private key to derive from, which the
     * store paths reject on their own terms with a better message than this could give.
     *
     * <p>A private key that will not decode is also passed over rather than raised. Storing a
     * key and deriving its public half are separate concerns, and this is the second one: making
     * {@code store} throw on input it accepted before would turn an optimisation into a new
     * validation rule, rejecting writes that used to succeed. The key is still stored, and the
     * load path still derives for anything left unstamped, so the cost is the round trip this
     * exists to avoid rather than a lost key.
     */
    public static void stampOn(@NonNull final KeyEntity key) {
        if (key.getPublicKey() != null || key.getPrivateKey() == null) {
            return;
        }
        try {
            key.setPublicKey(deriveFrom(key.getPrivateKey()));
        } catch (final RuntimeException e) {
            // By id and exception type only. The value that failed to decode is the private key.
            log.warn("Could not derive the public key of key {}, storing without one: {}",
                    key.getId(), e.getClass().getSimpleName());
        }
    }
}
