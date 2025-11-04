package xyz.tcheeric.cashu.vault.db.model;

import xyz.tcheeric.cashu.entities.annotation.Nut;

/**
 * Documentation for optional NUT-13 database schema enhancements.
 *
 * <p>This class documents optional database schema enhancements that add metadata
 * for deterministic secrets derived via NUT-13. These enhancements are NOT required
 * for NUT-13 functionality but provide valuable observability, analytics, and debugging
 * capabilities for production deployments.
 *
 * <h2>Purpose</h2>
 * <p>The enhancements allow mints to:
 * <ul>
 *   <li>Distinguish deterministic secrets from random secrets</li>
 *   <li>Track derivation counter values for gap analysis</li>
 *   <li>Generate analytics on wallet recovery patterns</li>
 *   <li>Debug recovery issues by examining counter sequences</li>
 *   <li>Optimize storage by identifying reused derivation paths</li>
 * </ul>
 *
 * <h2>Schema Changes</h2>
 *
 * <h3>New Columns for ProofEntity (t_proof table)</h3>
 * <pre>{@code
 * ALTER TABLE t_proof ADD COLUMN derivation_counter INTEGER NULL
 *     COMMENT 'Counter value used for NUT-13 deterministic secret derivation. NULL for random secrets.';
 *
 * ALTER TABLE t_proof ADD COLUMN is_deterministic BOOLEAN DEFAULT FALSE
 *     COMMENT 'Flag indicating if this proof uses a deterministic (NUT-13) secret.';
 *
 * CREATE INDEX idx_proof_deterministic ON t_proof(is_deterministic)
 *     WHERE is_deterministic = TRUE;
 *
 * CREATE INDEX idx_proof_derivation_counter ON t_proof(derivation_counter)
 *     WHERE derivation_counter IS NOT NULL;
 * }</pre>
 *
 * <h3>Audit Table (t_proof_a)</h3>
 * <pre>{@code
 * ALTER TABLE t_proof_a ADD COLUMN derivation_counter INTEGER NULL;
 * ALTER TABLE t_proof_a ADD COLUMN is_deterministic BOOLEAN DEFAULT FALSE;
 * }</pre>
 *
 * <h2>Benefits</h2>
 *
 * <h3>1. Analytics and Reporting</h3>
 * <pre>{@code
 * -- Count deterministic vs random proofs
 * SELECT
 *     is_deterministic,
 *     COUNT(*) as proof_count,
 *     SUM(amount) as total_amount
 * FROM t_proof
 * GROUP BY is_deterministic;
 *
 * -- Identify most active derivation counters
 * SELECT
 *     derivation_counter,
 *     COUNT(*) as usage_count
 * FROM t_proof
 * WHERE derivation_counter IS NOT NULL
 * GROUP BY derivation_counter
 * ORDER BY usage_count DESC
 * LIMIT 10;
 * }</pre>
 *
 * <h3>2. Gap Detection for Recovery Optimization</h3>
 * <pre>{@code
 * -- Find gaps in counter sequence for a specific keyset
 * -- (Helps identify lost tokens or incomplete recovery)
 * WITH counter_sequence AS (
 *     SELECT generate_series(0, MAX(derivation_counter)) AS counter
 *     FROM t_proof
 *     WHERE keyset_id = '009a1f293253e41e'
 *       AND derivation_counter IS NOT NULL
 * )
 * SELECT cs.counter
 * FROM counter_sequence cs
 * LEFT JOIN t_proof p
 *     ON p.derivation_counter = cs.counter
 *     AND p.keyset_id = '009a1f293253e41e'
 * WHERE p.secret IS NULL
 * ORDER BY cs.counter;
 * }</pre>
 *
 * <h3>3. Debugging Recovery Issues</h3>
 * <pre>{@code
 * -- Show all proofs for a keyset ordered by counter
 * SELECT
 *     derivation_counter,
 *     amount,
 *     state,
 *     created_at
 * FROM t_proof
 * WHERE is_deterministic = TRUE
 *   AND keyset_id = '009a1f293253e41e'
 * ORDER BY derivation_counter;
 *
 * -- Find duplicate counter usage (should never happen)
 * SELECT
 *     derivation_counter,
 *     COUNT(*) as usage_count
 * FROM t_proof
 * WHERE derivation_counter IS NOT NULL
 * GROUP BY derivation_counter
 * HAVING COUNT(*) > 1;
 * }</pre>
 *
 * <h3>4. Storage Optimization</h3>
 * <pre>{@code
 * -- Identify storage usage by secret type
 * SELECT
 *     is_deterministic,
 *     COUNT(*) as proof_count,
 *     pg_size_pretty(pg_total_relation_size('t_proof')) as table_size
 * FROM t_proof
 * GROUP BY is_deterministic;
 * }</pre>
 *
 * <h2>Implementation Guide</h2>
 *
 * <h3>Step 1: Add Fields to ProofEntity</h3>
 * <pre>{@code
 * @Entity(name = "proof")
 * @Table(name = "t_proof", indexes = {
 *     @Index(name = "idx_proof_mint_id", columnList = "mint_id"),
 *     @Index(name = "idx_proof_deterministic", columnList = "is_deterministic"),
 *     @Index(name = "idx_proof_derivation_counter", columnList = "derivation_counter")
 * })
 * public class ProofEntity extends BaseEntity {
 *
 *     // ... existing fields ...
 *
 *     @JsonProperty
 *     @Column(name = "derivation_counter", nullable = true)
 *     private Integer derivationCounter;
 *
 *     @JsonProperty
 *     @Column(name = "is_deterministic", nullable = false)
 *     private Boolean isDeterministic = false;
 *
 *     public static <T extends Secret> ProofEntity fromProof(Proof<T> proof, MintEntity mintEntity) {
 *         ProofEntity proofEntity = new ProofEntity();
 *         proofEntity.setAmount(proof.getAmount());
 *
 *         String yCoordinate = SecretUtil.toY(proof.getSecret());
 *         proofEntity.setSecret(yCoordinate);
 *
 *         // NEW: Set deterministic metadata if applicable
 *         if (proof.getSecret() instanceof DeterministicSecret deterministicSecret) {
 *             proofEntity.setIsDeterministic(true);
 *             proofEntity.setDerivationCounter(deterministicSecret.getCounter());
 *         } else {
 *             proofEntity.setIsDeterministic(false);
 *             proofEntity.setDerivationCounter(null);
 *         }
 *
 *         if (proof.getWitness() != null) {
 *             proofEntity.setWitness(proof.getWitness().toString());
 *         }
 *
 *         proofEntity.setUnblindedSignature(proof.getUnblindedSignature().toString());
 *         proofEntity.setMint(mintEntity);
 *         return proofEntity;
 *     }
 * }
 * }</pre>
 *
 * <h3>Step 2: Create Migration Script</h3>
 * <p>See: {@code resources/db/migration/V{version}__add_nut13_metadata.sql}
 *
 * <h3>Step 3: Update Tests</h3>
 * <p>Add tests to verify metadata is correctly populated:
 * <pre>{@code
 * @Test
 * void testDeterministicProofMetadata() {
 *     // Create deterministic secret
 *     DeterministicSecret secret = DeterministicSecret.create(
 *         derivedBytes,
 *         KeysetId.fromString("009a1f293253e41e"),
 *         42  // counter
 *     );
 *
 *     // Create proof
 *     Proof<DeterministicSecret> proof = new Proof<>();
 *     proof.setSecret(secret);
 *     proof.setAmount(100);
 *     // ... set other fields ...
 *
 *     // Convert to entity
 *     ProofEntity entity = ProofEntity.fromProof(proof, mintEntity);
 *
 *     // Verify metadata
 *     assertTrue(entity.getIsDeterministic());
 *     assertEquals(42, entity.getDerivationCounter());
 * }
 *
 * @Test
 * void testRandomProofMetadata() {
 *     // Create random secret
 *     RandomStringSecret secret = new RandomStringSecret();
 *
 *     // Create proof
 *     Proof<RandomStringSecret> proof = new Proof<>();
 *     proof.setSecret(secret);
 *     proof.setAmount(100);
 *     // ... set other fields ...
 *
 *     // Convert to entity
 *     ProofEntity entity = ProofEntity.fromProof(proof, mintEntity);
 *
 *     // Verify metadata
 *     assertFalse(entity.getIsDeterministic());
 *     assertNull(entity.getDerivationCounter());
 * }
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 *
 * <h3>Storage Overhead</h3>
 * <ul>
 *   <li><b>derivation_counter</b>: 4 bytes (INTEGER)</li>
 *   <li><b>is_deterministic</b>: 1 byte (BOOLEAN)</li>
 *   <li><b>Total per row</b>: ~5 bytes + index overhead</li>
 * </ul>
 *
 * <h3>Index Size Estimates</h3>
 * <p>For 1 million proofs:
 * <ul>
 *   <li>Data overhead: ~5 MB</li>
 *   <li>Index overhead: ~10-15 MB</li>
 *   <li>Total: ~20 MB (negligible for most deployments)</li>
 * </ul>
 *
 * <h3>Query Performance</h3>
 * <p>The indexes dramatically improve query performance for:
 * <ul>
 *   <li>Filtering by deterministic flag: O(log n) instead of O(n)</li>
 *   <li>Range queries on counter: O(log n) lookup</li>
 *   <li>Gap detection: Efficient with proper indexes</li>
 * </ul>
 *
 * <h2>Security Considerations</h2>
 *
 * <h3>Privacy</h3>
 * <p><b>IMPORTANT</b>: The derivation counter reveals information about wallet usage patterns:
 * <ul>
 *   <li>Counter values indicate the order tokens were minted</li>
 *   <li>Sequential counters from the same keyset suggest same wallet</li>
 *   <li>Gaps in counter sequence may indicate failed minting attempts</li>
 * </ul>
 *
 * <p><b>Mitigation Strategies:</b>
 * <ul>
 *   <li>Restrict access to database to authorized personnel only</li>
 *   <li>Use database-level encryption for sensitive columns</li>
 *   <li>Consider anonymizing data in analytics exports</li>
 *   <li>Aggregate statistics before sharing publicly</li>
 * </ul>
 *
 * <h3>Access Control</h3>
 * <pre>{@code
 * -- Grant read-only access for analytics role
 * GRANT SELECT ON t_proof TO analytics_user;
 *
 * -- Revoke sensitive data access from general users
 * REVOKE SELECT (derivation_counter) ON t_proof FROM public;
 * }</pre>
 *
 * <h2>Monitoring Queries</h2>
 *
 * <h3>Daily Health Check</h3>
 * <pre>{@code
 * -- Check for counter reuse (should be empty)
 * SELECT COUNT(*)
 * FROM (
 *     SELECT derivation_counter
 *     FROM t_proof
 *     WHERE derivation_counter IS NOT NULL
 *     GROUP BY derivation_counter, keyset_id
 *     HAVING COUNT(*) > 1
 * ) AS duplicates;
 *
 * -- Monitor deterministic adoption rate
 * SELECT
 *     DATE(created_at) as date,
 *     SUM(CASE WHEN is_deterministic THEN 1 ELSE 0 END)::FLOAT /
 *     COUNT(*)::FLOAT * 100 as deterministic_percentage
 * FROM t_proof
 * WHERE created_at >= NOW() - INTERVAL '30 days'
 * GROUP BY DATE(created_at)
 * ORDER BY date;
 * }</pre>
 *
 * <h2>Rollback Procedure</h2>
 * <p>If you need to remove the enhancements:
 * <pre>{@code
 * -- Drop indexes first
 * DROP INDEX IF EXISTS idx_proof_deterministic;
 * DROP INDEX IF EXISTS idx_proof_derivation_counter;
 *
 * -- Drop columns
 * ALTER TABLE t_proof DROP COLUMN IF EXISTS derivation_counter;
 * ALTER TABLE t_proof DROP COLUMN IF EXISTS is_deterministic;
 *
 * -- Update audit table
 * ALTER TABLE t_proof_a DROP COLUMN IF EXISTS derivation_counter;
 * ALTER TABLE t_proof_a DROP COLUMN IF EXISTS is_deterministic;
 * }</pre>
 *
 * <h2>Alternative: NoSQL Approach</h2>
 * <p>For mints using NoSQL databases, consider storing metadata as embedded document:
 * <pre>{@code
 * {
 *   "secret": "abc123...",
 *   "amount": 100,
 *   "keyset_id": "009a1f293253e41e",
 *   "metadata": {
 *     "type": "deterministic",
 *     "derivation": {
 *       "counter": 42,
 *       "path": "m/129372'/0'/123456789'/42'/0"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a></li>
 *   <li>{@link xyz.tcheeric.cashu.common.DeterministicSecret}</li>
 *   <li>{@link ProofEntity}</li>
 *   <li>{@link xyz.tcheeric.cashu.wallet.proto.state.DerivationStateManager}</li>
 * </ul>
 *
 * @see ProofEntity
 * @see xyz.tcheeric.cashu.common.DeterministicSecret
 * @see xyz.tcheeric.cashu.wallet.proto.state.DerivationStateManager
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@Nut(13)
public final class NUT13SchemaEnhancement {

    /**
     * Private constructor to prevent instantiation.
     * This is a documentation class only.
     */
    private NUT13SchemaEnhancement() {
        throw new AssertionError("Documentation class cannot be instantiated");
    }
}
