-- UNIQUE (unit, mint_id) allowed a mint exactly one keyset per unit, ever, which
-- made keyset rotation impossible: the replacement could not be inserted while
-- the keyset it replaces still existed. Deleting the old one to free the slot is
-- not an option — NUT-02 archived keysets must go on verifying and redeeming
-- indefinitely, so removing one strands every token it ever signed.
--
-- The invariant worth keeping is narrower: a mint has at most one *active*
-- keyset per unit, so there is never ambiguity about which keyset signs.
-- Archived keysets may accumulate freely.
--
-- Identity is unaffected: idx_keyset_key_set_mint_unq already prevents the same
-- keyset id being registered twice for a mint.

DROP INDEX IF EXISTS idx_keyset_unit_mint_unq;

CREATE UNIQUE INDEX idx_keyset_unit_mint_active_unq
    ON t_keyset (unit, mint_id)
    WHERE archived = false;
