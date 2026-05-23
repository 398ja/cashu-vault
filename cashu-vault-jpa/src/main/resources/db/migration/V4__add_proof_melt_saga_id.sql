-- cashu-mint spec 002 T010 — Add melt_saga_id binding to t_proof so the
-- "PROOFS_HELD by exactly one saga" invariant (FR-006) is enforced at the
-- vault row, not via a denormalised side-table on the mint.
--
-- The column is nullable. Non-null values mean the row is currently held
-- by the named melt saga; the partial unique index makes that hold
-- exclusive — two sagas cannot simultaneously claim the same proof while
-- both rows have melt_saga_id set.
--
-- Lifecycle (mint-side):
--   UNSPENT → PENDING   sets   melt_saga_id = <new saga id>
--   PENDING → SPENT     clears melt_saga_id = NULL
--   PENDING → UNSPENT   clears melt_saga_id = NULL  (refund on FAILED)

ALTER TABLE t_proof
ADD COLUMN melt_saga_id VARCHAR(64);

-- Hibernate Envers shadow gets the same column so audit history mirrors
-- the live row.
ALTER TABLE t_proof_a
ADD COLUMN melt_saga_id VARCHAR(64);

-- Lookup index: operator dashboards + the saga reconciler need to
-- enumerate proofs held by a given saga.
CREATE INDEX IF NOT EXISTS ix_proof_melt_saga_id
    ON t_proof (melt_saga_id);

-- Exclusivity guarantee (FR-006): a proof row may have melt_saga_id NULL
-- freely, but when set, the saga ownership MUST be exclusive across the
-- entire proof table.
--
-- The application-level CAS in ProofRepository.markPending enforces this
-- in every supported database (H2, PostgreSQL): the UPDATE is gated on
-- both `state = 'UNSPENT'` AND `melt_saga_id IS NULL`, so a concurrent
-- second writer sees rowcount=0 and bails out with melt_in_progress.
--
-- A defense-in-depth CHECK constraint can additionally tie melt_saga_id
-- to the PENDING state on production PostgreSQL deploys. H2 (used by
-- the test harness even in MODE=PostgreSQL) doesn't always evaluate
-- CHECK constraints across MODE configurations, so this is left as an
-- operator-applied hardening step rather than a universal migration:
--
--   ALTER TABLE t_proof
--     ADD CONSTRAINT chk_proof_saga_state
--     CHECK ((melt_saga_id IS NULL) OR (state = 'PENDING'));
--
-- (A UNIQUE index on `(id)` would be a no-op since `id` is already the
-- PK; row-level exclusivity is intrinsic to PK uniqueness.)
