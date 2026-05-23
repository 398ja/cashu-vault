-- Migration: Add unique constraints and fingerprint column to t_proof
-- Purpose: Prevents duplicate proof storage at database level (security hardening)
--
-- The (mint_id, secret) combination uniquely identifies a proof:
-- - Same proof at different mints is technically possible (rare)
-- - Same secret should never appear twice for the same mint
--
-- The (mint_id, C) combination prevents duplicate commitment storage:
-- - Commitment (C) is the unblinded signature from the mint
-- - Each commitment should be unique per mint

-- Add fingerprint column for SHA-256 token fingerprint storage
ALTER TABLE t_proof
ADD COLUMN fingerprint VARCHAR(64);

-- Add corresponding column to audit table
ALTER TABLE t_proof_a
ADD COLUMN fingerprint VARCHAR(64);

-- Add unique constraint on (mint_id, secret)
-- Prevents storing same secret twice for a given mint
ALTER TABLE t_proof
ADD CONSTRAINT uk_proof_mint_secret UNIQUE (mint_id, secret);

-- Add unique constraint on (mint_id, C)
-- Prevents storing same commitment (unblinded signature) twice for a given mint
ALTER TABLE t_proof
ADD CONSTRAINT uk_proof_mint_commitment UNIQUE (mint_id, c);

-- Add index on commitment for spent proof lookups (performance)
CREATE INDEX IF NOT EXISTS idx_proof_commitment ON t_proof(c);

-- Add index on fingerprint for duplicate detection lookups
CREATE INDEX IF NOT EXISTS idx_proof_fingerprint ON t_proof(fingerprint);

-- Note: COMMENT ON CONSTRAINT is not supported by H2
-- PostgreSQL production environments can add these comments manually:
-- COMMENT ON CONSTRAINT uk_proof_mint_secret ON t_proof IS 'Prevents duplicate proof storage';
-- COMMENT ON CONSTRAINT uk_proof_mint_commitment ON t_proof IS 'Prevents duplicate commitment storage';
-- COMMENT ON COLUMN t_proof.fingerprint IS 'SHA-256 token fingerprint for duplicate detection';
