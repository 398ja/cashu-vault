-- Remove cryptographic material from the proof audit history.
--
-- Audit finding H-8. t_proof_a is an Envers history table: every insert, update and delete of a
-- proof wrote a full row copy, including the unblinded signature C and the NUT-11 witness. The
-- history row survived deletion of the live row, so deleting a proof did not remove its material.
--
-- The audit trail's job is to record that a proof moved between states and when. It does not need
-- a second, permanent copy of the cryptographic material to do that. The entity now marks both
-- columns @NotAudited, which stops new history rows carrying them; this migration removes the
-- ones already written and drops the columns so they cannot come back.
--
-- Numbered V1000, not V9, deliberately. V999__add_nut13_derivation_metadata.sql is already
-- released and its ALTER statements are live, so every database that has ever booted this
-- service is at schema version 999. A migration numbered 9 is out of order against that, and
-- with spring.flyway.out-of-order defaulting to false and validate-on-migrate defaulting to
-- true, Flyway fails the migrate with "Detected resolved migration not applied to database: 9"
-- and the application does not start. A fresh database applies 1..9 then 999 and works, so the
-- failure would have passed CI and only appeared on a deployment that had real data to protect.
--
-- Note on `secret`: that column holds Y = hash_to_curve(secret), not the secret itself (see
-- MintProtocolUtil.toProofEntity and ProofEntity.fromProof). It is a one-way image of the secret
-- and cannot be spent, so it stays: state history keyed on Y is exactly what makes the audit
-- trail useful for double-spend investigation.

ALTER TABLE t_proof_a DROP COLUMN IF EXISTS c;
ALTER TABLE t_proof_a DROP COLUMN IF EXISTS witness;
