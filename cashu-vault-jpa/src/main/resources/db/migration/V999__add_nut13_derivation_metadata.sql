-- ============================================================================
-- NUT-13 Optional Schema Enhancement: Add Derivation Metadata
-- ============================================================================
--
-- Purpose: Add optional metadata columns to track deterministic secret
--          derivation information for analytics, debugging, and optimization.
--
-- Version: V999 (placeholder - adjust version number based on your migration sequence)
-- Author: NUT-13 Implementation Team
-- Date: 2025-11-04
--
-- References:
--   - NUT-13 Specification: https://github.com/cashubtc/nuts/blob/main/13.md
--   - Documentation: xyz.tcheeric.cashu.vault.db.model.NUT13SchemaEnhancement
--
-- IMPORTANT: This migration is OPTIONAL. NUT-13 functionality works without it.
--            These columns provide observability and analytics benefits only.
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Add Columns to Main Proof Table
-- ----------------------------------------------------------------------------

-- Add derivation counter column (NULL for random secrets)
-- Counter value used for NUT-13 deterministic secret derivation. NULL for random secrets.
ALTER TABLE t_proof
ADD COLUMN derivation_counter INTEGER NULL;

-- Add deterministic flag column
-- Flag indicating if this proof uses a deterministic (NUT-13) secret.
ALTER TABLE t_proof
ADD COLUMN is_deterministic BOOLEAN DEFAULT FALSE NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. Create Indexes for Query Performance
-- ----------------------------------------------------------------------------

-- Index for filtering deterministic proofs
-- Optimizes queries filtering for deterministic proofs only.
-- Note: H2 doesn't support partial indexes with WHERE clause
CREATE INDEX idx_proof_deterministic
ON t_proof(is_deterministic);

-- Index for counter-based queries and gap analysis
-- Optimizes counter range queries and gap detection for recovery analysis.
CREATE INDEX idx_proof_derivation_counter
ON t_proof(derivation_counter);

-- Composite index for mint + counter queries
-- Optimizes gap detection queries for specific mints.
CREATE INDEX idx_proof_mint_counter
ON t_proof(mint_id, derivation_counter);

-- ----------------------------------------------------------------------------
-- 3. Add Columns to Audit Table (Hibernate Envers)
-- ----------------------------------------------------------------------------

-- Add derivation counter to audit table
ALTER TABLE t_proof_a
ADD COLUMN derivation_counter INTEGER NULL;

-- Add deterministic flag to audit table
ALTER TABLE t_proof_a
ADD COLUMN is_deterministic BOOLEAN DEFAULT FALSE;

-- ----------------------------------------------------------------------------
-- 4. Initialize Existing Data (if any)
-- ----------------------------------------------------------------------------

-- Mark all existing proofs as non-deterministic (safe default)
-- This assumes existing proofs use random secrets
UPDATE t_proof
SET is_deterministic = FALSE,
    derivation_counter = NULL
WHERE is_deterministic IS NULL;

-- ----------------------------------------------------------------------------
-- 5. Verify Migration Success
-- ----------------------------------------------------------------------------

-- Verification queries (run manually after migration)
-- 1. Check column exists:
--    SELECT column_name, data_type, is_nullable
--    FROM information_schema.columns
--    WHERE table_name = 't_proof'
--    AND column_name IN ('derivation_counter', 'is_deterministic');
--
-- 2. Check indexes exist:
--    SELECT indexname, indexdef
--    FROM pg_indexes
--    WHERE tablename = 't_proof'
--    AND indexname LIKE '%derivation%';
--
-- 3. Verify data integrity:
--    SELECT
--        is_deterministic,
--        COUNT(*) as count,
--        COUNT(derivation_counter) as with_counter
--    FROM t_proof
--    GROUP BY is_deterministic;

-- ============================================================================
-- Rollback Script (if needed)
-- ============================================================================
--
-- To rollback this migration:
--
-- DROP INDEX IF EXISTS idx_proof_keyset_counter;
-- DROP INDEX IF EXISTS idx_proof_derivation_counter;
-- DROP INDEX IF EXISTS idx_proof_deterministic;
--
-- ALTER TABLE t_proof_a DROP COLUMN IF EXISTS is_deterministic;
-- ALTER TABLE t_proof_a DROP COLUMN IF EXISTS derivation_counter;
--
-- ALTER TABLE t_proof DROP COLUMN IF EXISTS is_deterministic;
-- ALTER TABLE t_proof DROP COLUMN IF EXISTS derivation_counter;
--
-- ============================================================================
