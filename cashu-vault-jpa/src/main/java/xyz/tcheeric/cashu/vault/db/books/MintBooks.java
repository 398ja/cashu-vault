package xyz.tcheeric.cashu.vault.db.books;

import java.time.Instant;
import java.util.List;

/**
 * The amounts a mint's books reconciler needs from the vault, as of {@code until}
 * (cashu-vault#156, cashu-mint#515).
 *
 * <p>Amounts and counts only. Nothing here identifies a proof: no secret, signature, witness or
 * hold id, so the read token can serve it without handing out ecash.
 *
 * @param until             the watermark: every spend at or before it is counted, none after
 * @param spent             spent value per keyset, for proofs that carry a keyset id
 * @param pending           value currently held by a melt or swap, per keyset. This is the state
 *                          at the time of the request, not at {@code until}: a hold has no history
 * @param unattributedSpent spent proofs with no keyset id (rows written before V14)
 */
public record MintBooks(Instant until,
                        List<SpentByKeyset> spent,
                        List<PendingByKeyset> pending,
                        Unattributed unattributedSpent) {

    /** Spent value on one keyset. {@code unit} is null if the keyset is unknown to the vault. */
    public record SpentByKeyset(String keysetId, String unit, long amount, long count) {
    }

    /** Held value on one keyset for one kind of hold. {@code keysetId} is null for legacy rows. */
    public record PendingByKeyset(String keysetId, String holdKind, long amount, long holds) {
    }

    /** Spent value that cannot be attributed to a keyset. */
    public record Unattributed(long amount, long count) {
    }
}
