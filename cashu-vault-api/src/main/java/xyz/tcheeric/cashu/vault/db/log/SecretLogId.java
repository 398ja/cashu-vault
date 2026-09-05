package xyz.tcheeric.cashu.vault.db.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A stable, non-reversible identifier for a secret, for use in logs.
 *
 * <h2>Why</h2>
 *
 * <p>A proof's {@code secret} plus {@code C} is spendable ecash. The vault logged both verbatim
 * at INFO and DEBUG on the retrieval paths (audit M-12), and the base profile additionally set
 * {@code spring.jpa.show-sql=true} with Hibernate SQL at DEBUG, so bound parameters printed too.
 * Logs are routinely shipped to systems with a wider audience than the database, so anyone with
 * log access could reconstruct spendable tokens.
 *
 * <p>Operators still need to correlate a request with a row, which is the legitimate reason the
 * value was being logged. A truncated SHA-256 preserves that: the same secret always produces
 * the same identifier, and the identifier reveals nothing about the secret.
 *
 * <p>16 hex characters (64 bits) is short enough to read in a log line and wide enough that
 * collisions are not a practical concern at any realistic proof volume.
 */
public final class SecretLogId {

    private static final int HEX_CHARS = 16;

    private SecretLogId() {
    }

    /**
     * Returns a truncated SHA-256 of the value, or a marker for null or blank input.
     *
     * @param value the secret, signature or other sensitive identifier
     * @return a short non-reversible identifier safe to log
     */
    public static String of(String value) {
        if (value == null) {
            return "<null>";
        }
        if (value.isBlank()) {
            return "<blank>";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(HEX_CHARS);
            for (int i = 0; i < HEX_CHARS / 2; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256. If it is somehow absent, log nothing rather than the
            // secret itself.
            return "<unhashable>";
        }
    }
}
