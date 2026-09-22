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
-- Renumbered from V1000 to V10 (issue #128). It was originally numbered 1000 to sort after
-- the released V999__add_nut13_derivation_metadata.sql: a migration numbered 9 was out of
-- order against a database already at version 999, and with out-of-order defaulting to false
-- Flyway failed the migrate and the application did not start. That worked, but it left every
-- future migration needing a number above 1000 to stay ordered, which is the trap rather than
-- an escape from it. V999 is now V9 and V11 repairs deployed histories, so ordinary numbering
-- resumes here.
--
-- Note on `secret`: that column holds Y = hash_to_curve(secret), not the secret itself (see
-- MintProtocolUtil.toProofEntity and ProofEntity.fromProof). It is a one-way image of the secret
-- and cannot be spent, so it stays: state history keyed on Y is exactly what makes the audit
-- trail useful for double-spend investigation.

ALTER TABLE t_proof_a DROP COLUMN IF EXISTS c;
ALTER TABLE t_proof_a DROP COLUMN IF EXISTS witness;
