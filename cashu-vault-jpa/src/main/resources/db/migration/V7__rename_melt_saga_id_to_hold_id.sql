-- Two flows now take an exclusive hold on a proof: the melt saga, which the
-- column was named for, and the swap hold added for cashu-mint#400. They share
-- one column deliberately -- that is what makes a swap hold block a melt on the
-- same proof, and vice versa -- but the name said only one of them.
--
-- That matters more than tidiness, because the two resolve in OPPOSITE
-- directions. A stale melt hold is released: no payment went out, so the wallet
-- should get its money back. A stale swap hold that reached signing must be
-- committed: an output may already be redeemable, and releasing the inputs on
-- top of it is a double spend. An operator reading a held row had to infer
-- which flow produced it from a `swap-` prefix on the id before they could know
-- which action was safe.
--
-- So the column is named for what it holds rather than for one of its two
-- users, and hold_kind states the flow outright.

ALTER TABLE t_proof
    RENAME COLUMN melt_saga_id TO hold_id;

ALTER TABLE t_proof
    ADD COLUMN hold_kind VARCHAR(8);

ALTER TABLE t_proof_a
    RENAME COLUMN melt_saga_id TO hold_id;

ALTER TABLE t_proof_a
    ADD COLUMN hold_kind VARCHAR(8);

-- Existing holds are classified from the prefix the swap hold used, which is
-- exactly the inference this column exists to remove. It is reliable here
-- because it is applied once, to rows written before the column existed.
UPDATE t_proof
   SET hold_kind = CASE
                       WHEN hold_id IS NULL THEN NULL
                       WHEN hold_id LIKE 'swap-%' THEN 'SWAP'
                       ELSE 'MELT'
                   END;

DROP INDEX IF EXISTS ix_proof_melt_saga_id;

CREATE INDEX IF NOT EXISTS ix_proof_hold_id
    ON t_proof (hold_id);
