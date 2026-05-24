-- Migration: V4 — Tombstone columns + Envers principal-id revision tracking
-- Purpose:  Append-only deletion (tombstone) + auditable principal capture
-- Spec:     001-vault-append-only-scoped-lookup
-- Safety:   Nullable ADD COLUMN on PostgreSQL >= 11 does NOT rewrite the table.

-- ----------------------------------------------------------------------------
-- 1. Live proof table
-- ----------------------------------------------------------------------------
ALTER TABLE t_proof
    ADD COLUMN tombstoned_at TIMESTAMP WITHOUT TIME ZONE NULL;

ALTER TABLE t_proof
    ADD COLUMN tombstoned_by VARCHAR(255) NULL;

-- ----------------------------------------------------------------------------
-- 2. Envers audit table (lockstep — FR-010)
-- ----------------------------------------------------------------------------
ALTER TABLE t_proof_a
    ADD COLUMN tombstoned_at TIMESTAMP WITHOUT TIME ZONE NULL;

ALTER TABLE t_proof_a
    ADD COLUMN tombstoned_by VARCHAR(255) NULL;

-- ----------------------------------------------------------------------------
-- 3. Revision-info table — principal capture
-- ----------------------------------------------------------------------------
ALTER TABLE revinfo
    ADD COLUMN principal_id VARCHAR(255) NULL;
