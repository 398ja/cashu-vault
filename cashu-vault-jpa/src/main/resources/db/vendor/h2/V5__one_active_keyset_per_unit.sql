-- H2 counterpart of the PostgreSQL migration of the same version; see that file
-- for why the constraint changes.
--
-- H2 has no partial indexes, so the same invariant is expressed with a generated
-- column that is NULL for archived rows. NULLs do not collide in a unique index,
-- so archived keysets accumulate freely while at most one active keyset exists
-- per (unit, mint_id).

DROP INDEX IF EXISTS idx_keyset_unit_mint_unq;

ALTER TABLE t_keyset
    ADD COLUMN active_slot INT GENERATED ALWAYS AS (CASE WHEN archived THEN NULL ELSE 0 END);

CREATE UNIQUE INDEX idx_keyset_unit_mint_active_unq
    ON t_keyset (unit, mint_id, active_slot);
