package xyz.tcheeric.cashu.vault.db.model;

/**
 * Which flow holds a proof.
 *
 * <p>A melt saga and a swap both take an exclusive hold on a proof through the same binding, which
 * is what makes one block the other on the same proof. They resolve in opposite directions though,
 * so an unresolved hold cannot be acted on until this is known: a stale melt hold is released,
 * because no payment went out and the wallet should get its money back, while a stale swap hold
 * that reached signing must be committed, because an output may already be redeemable and
 * releasing the inputs on top of it spends the same value twice.
 *
 * <p>Recorded rather than inferred from the hold id, since guessing wrong in the swap case is a
 * double spend.
 */
public enum HoldKind {

    /** Held by a NUT-05 melt saga. */
    MELT,

    /** Held by a NUT-03 swap across its signing step. */
    SWAP;

    /**
     * The prefix the mint puts on a swap hold id.
     *
     * <p>Kept here so the one place that still reads a kind out of an id is the one place that
     * defines the convention.
     */
    private static final String SWAP_HOLD_PREFIX = "swap-";

    /**
     * Classifies a hold id by its prefix.
     *
     * <p>This is the inference {@link HoldKind} exists to replace, kept only for writing the value
     * in the first place. Callers deciding how to resolve a hold must read the recorded kind
     * instead, because a hold id is caller-supplied and a wrong guess on a swap hold spends the
     * same value twice.
     */
    public static HoldKind forHoldId(String holdId) {
        return holdId != null && holdId.startsWith(SWAP_HOLD_PREFIX) ? SWAP : MELT;
    }
}
