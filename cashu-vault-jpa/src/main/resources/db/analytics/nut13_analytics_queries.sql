-- ============================================================================
-- NUT-13 Analytics Queries
-- ============================================================================
--
-- This file contains example SQL queries for analyzing NUT-13 deterministic
-- secret usage patterns, recovery optimization, and debugging.
--
-- Prerequisites:
--   - V999__add_nut13_derivation_metadata.sql migration applied
--   - Some deterministic proofs minted (is_deterministic = TRUE)
--
-- References:
--   - Documentation: xyz.tcheeric.cashu.vault.db.model.NUT13SchemaEnhancement
--   - NUT-13 Spec: https://github.com/cashubtc/nuts/blob/main/13.md
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BASIC STATISTICS
-- ----------------------------------------------------------------------------

-- Count deterministic vs random proofs
SELECT
    is_deterministic,
    COUNT(*) as proof_count,
    SUM(amount) as total_amount,
    ROUND(COUNT(*)::NUMERIC / SUM(COUNT(*)) OVER () * 100, 2) as percentage
FROM t_proof
GROUP BY is_deterministic
ORDER BY is_deterministic;

-- Deterministic adoption trend over time
SELECT
    DATE(created_at) as date,
    COUNT(*) as total_proofs,
    SUM(CASE WHEN is_deterministic THEN 1 ELSE 0 END) as deterministic_proofs,
    ROUND(
        SUM(CASE WHEN is_deterministic THEN 1 ELSE 0 END)::NUMERIC /
        COUNT(*)::NUMERIC * 100,
        2
    ) as deterministic_percentage
FROM t_proof
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date;

-- ----------------------------------------------------------------------------
-- 2. COUNTER ANALYSIS
-- ----------------------------------------------------------------------------

-- Most frequently used counters (across all keysets)
SELECT
    derivation_counter,
    COUNT(*) as usage_count,
    COUNT(DISTINCT keyset_id) as keyset_count
FROM t_proof
WHERE derivation_counter IS NOT NULL
GROUP BY derivation_counter
ORDER BY usage_count DESC
LIMIT 20;

-- Counter distribution per keyset
SELECT
    keyset_id,
    COUNT(*) as proof_count,
    MIN(derivation_counter) as min_counter,
    MAX(derivation_counter) as max_counter,
    MAX(derivation_counter) - MIN(derivation_counter) + 1 as counter_range,
    COUNT(*) - (MAX(derivation_counter) - MIN(derivation_counter) + 1) as gap_count_estimate
FROM t_proof
WHERE derivation_counter IS NOT NULL
GROUP BY keyset_id
ORDER BY proof_count DESC;

-- ----------------------------------------------------------------------------
-- 3. GAP DETECTION
-- ----------------------------------------------------------------------------

-- Find gaps in counter sequence for a specific keyset
-- Replace '009a1f293253e41e' with actual keyset ID
WITH counter_sequence AS (
    SELECT generate_series(
        (SELECT MIN(derivation_counter) FROM t_proof WHERE keyset_id = '009a1f293253e41e'),
        (SELECT MAX(derivation_counter) FROM t_proof WHERE keyset_id = '009a1f293253e41e')
    ) AS counter
)
SELECT
    cs.counter as missing_counter
FROM counter_sequence cs
LEFT JOIN t_proof p
    ON p.derivation_counter = cs.counter
    AND p.keyset_id = '009a1f293253e41e'
WHERE p.secret IS NULL
ORDER BY cs.counter;

-- Summary of gaps per keyset
WITH keyset_ranges AS (
    SELECT
        keyset_id,
        MIN(derivation_counter) as min_counter,
        MAX(derivation_counter) as max_counter,
        COUNT(DISTINCT derivation_counter) as unique_counters
    FROM t_proof
    WHERE derivation_counter IS NOT NULL
    GROUP BY keyset_id
)
SELECT
    keyset_id,
    min_counter,
    max_counter,
    unique_counters,
    (max_counter - min_counter + 1) as expected_counters,
    (max_counter - min_counter + 1) - unique_counters as gap_count
FROM keyset_ranges
WHERE (max_counter - min_counter + 1) > unique_counters
ORDER BY gap_count DESC;

-- ----------------------------------------------------------------------------
-- 4. RECOVERY ANALYSIS
-- ----------------------------------------------------------------------------

-- Proofs by keyset, ordered by counter (recovery view)
SELECT
    derivation_counter,
    amount,
    state,
    LEFT(secret, 16) || '...' as secret_preview,
    created_at
FROM t_proof
WHERE keyset_id = '009a1f293253e41e'
  AND is_deterministic = TRUE
ORDER BY derivation_counter;

-- Recovery efficiency: consecutive counter usage
WITH counter_sequences AS (
    SELECT
        keyset_id,
        derivation_counter,
        derivation_counter - LAG(derivation_counter, 1, derivation_counter - 1)
            OVER (PARTITION BY keyset_id ORDER BY derivation_counter) as gap_size
    FROM t_proof
    WHERE derivation_counter IS NOT NULL
)
SELECT
    keyset_id,
    COUNT(*) as total_proofs,
    SUM(CASE WHEN gap_size = 1 THEN 1 ELSE 0 END) as consecutive_proofs,
    ROUND(
        SUM(CASE WHEN gap_size = 1 THEN 1 ELSE 0 END)::NUMERIC /
        COUNT(*)::NUMERIC * 100,
        2
    ) as consecutive_percentage,
    AVG(gap_size) as avg_gap_size
FROM counter_sequences
GROUP BY keyset_id
ORDER BY consecutive_percentage DESC;

-- ----------------------------------------------------------------------------
-- 5. DUPLICATE DETECTION (SHOULD BE EMPTY)
-- ----------------------------------------------------------------------------

-- Find duplicate counter usage within same keyset (critical error if found)
SELECT
    keyset_id,
    derivation_counter,
    COUNT(*) as duplicate_count,
    ARRAY_AGG(secret) as secrets
FROM t_proof
WHERE derivation_counter IS NOT NULL
GROUP BY keyset_id, derivation_counter
HAVING COUNT(*) > 1;

-- Overall duplicate check
SELECT
    CASE
        WHEN COUNT(*) = 0 THEN 'PASS: No duplicate counters found'
        ELSE 'FAIL: ' || COUNT(*) || ' duplicate counter(s) detected!'
    END as duplicate_check
FROM (
    SELECT keyset_id, derivation_counter
    FROM t_proof
    WHERE derivation_counter IS NOT NULL
    GROUP BY keyset_id, derivation_counter
    HAVING COUNT(*) > 1
) as duplicates;

-- ----------------------------------------------------------------------------
-- 6. STATE ANALYSIS
-- ----------------------------------------------------------------------------

-- Proof state distribution for deterministic proofs
SELECT
    state,
    COUNT(*) as proof_count,
    SUM(amount) as total_amount,
    ROUND(COUNT(*)::NUMERIC / SUM(COUNT(*)) OVER () * 100, 2) as percentage
FROM t_proof
WHERE is_deterministic = TRUE
GROUP BY state
ORDER BY proof_count DESC;

-- Spent vs unspent by counter range
SELECT
    FLOOR(derivation_counter / 100) * 100 as counter_bucket,
    COUNT(*) as total_proofs,
    SUM(CASE WHEN state = 'SPENT' THEN 1 ELSE 0 END) as spent_proofs,
    SUM(CASE WHEN state = 'UNSPENT' THEN 1 ELSE 0 END) as unspent_proofs,
    ROUND(
        SUM(CASE WHEN state = 'SPENT' THEN 1 ELSE 0 END)::NUMERIC /
        COUNT(*)::NUMERIC * 100,
        2
    ) as spent_percentage
FROM t_proof
WHERE derivation_counter IS NOT NULL
GROUP BY FLOOR(derivation_counter / 100)
ORDER BY counter_bucket;

-- ----------------------------------------------------------------------------
-- 7. PERFORMANCE MONITORING
-- ----------------------------------------------------------------------------

-- Index usage statistics
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
WHERE tablename = 't_proof'
  AND indexname LIKE '%derivation%'
ORDER BY idx_scan DESC;

-- Table size with NUT-13 columns
SELECT
    pg_size_pretty(pg_total_relation_size('t_proof')) as total_size,
    pg_size_pretty(pg_relation_size('t_proof')) as table_size,
    pg_size_pretty(pg_total_relation_size('t_proof') - pg_relation_size('t_proof')) as indexes_size;

-- ----------------------------------------------------------------------------
-- 8. DEBUGGING QUERIES
-- ----------------------------------------------------------------------------

-- Recent deterministic proofs with full details
SELECT
    p.secret,
    p.amount,
    p.derivation_counter,
    p.keyset_id,
    p.state,
    p.created_at,
    m.url as mint_url
FROM t_proof p
JOIN t_mint m ON p.mint_id = m.id
WHERE p.is_deterministic = TRUE
ORDER BY p.created_at DESC
LIMIT 50;

-- Keyset activity summary
SELECT
    p.keyset_id,
    COUNT(*) as total_proofs,
    SUM(CASE WHEN p.is_deterministic THEN 1 ELSE 0 END) as deterministic_count,
    MIN(p.derivation_counter) as min_counter,
    MAX(p.derivation_counter) as max_counter,
    MIN(p.created_at) as first_proof,
    MAX(p.created_at) as last_proof
FROM t_proof p
GROUP BY p.keyset_id
HAVING SUM(CASE WHEN p.is_deterministic THEN 1 ELSE 0 END) > 0
ORDER BY deterministic_count DESC;

-- Counter progression over time for a keyset
SELECT
    DATE(created_at) as date,
    MIN(derivation_counter) as min_counter_that_day,
    MAX(derivation_counter) as max_counter_that_day,
    COUNT(*) as proofs_minted
FROM t_proof
WHERE keyset_id = '009a1f293253e41e'
  AND derivation_counter IS NOT NULL
GROUP BY DATE(created_at)
ORDER BY date;

-- ----------------------------------------------------------------------------
-- 9. EXPORT QUERIES FOR EXTERNAL ANALYSIS
-- ----------------------------------------------------------------------------

-- Export deterministic proof summary (CSV-friendly)
-- Copy output to CSV for analysis in spreadsheet/pandas
SELECT
    keyset_id,
    derivation_counter,
    amount,
    state,
    TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') as created_timestamp
FROM t_proof
WHERE is_deterministic = TRUE
ORDER BY keyset_id, derivation_counter;

-- Export gap analysis summary
SELECT
    keyset_id,
    STRING_AGG(missing_counter::TEXT, ',' ORDER BY missing_counter) as gap_list
FROM (
    -- Reuse gap detection query from section 3
    SELECT
        p.keyset_id,
        cs.counter as missing_counter
    FROM t_proof p
    CROSS JOIN LATERAL (
        SELECT generate_series(
            MIN(derivation_counter) OVER (PARTITION BY keyset_id),
            MAX(derivation_counter) OVER (PARTITION BY keyset_id)
        ) AS counter
    ) cs
    LEFT JOIN t_proof p2
        ON p2.derivation_counter = cs.counter
        AND p2.keyset_id = p.keyset_id
    WHERE p2.secret IS NULL
      AND p.is_deterministic = TRUE
    GROUP BY p.keyset_id, cs.counter
) gaps
GROUP BY keyset_id;

-- ============================================================================
-- MAINTENANCE QUERIES
-- ============================================================================

-- Vacuum and analyze after bulk updates
-- VACUUM ANALYZE t_proof;

-- Rebuild indexes if needed
-- REINDEX TABLE t_proof;

-- Update statistics for query planner
-- ANALYZE t_proof;

-- ============================================================================
