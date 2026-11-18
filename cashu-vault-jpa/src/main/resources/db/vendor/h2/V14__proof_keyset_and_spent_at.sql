-- H2 counterpart of the PostgreSQL migration of the same version (cashu-vault#156).
--
-- Same columns. H2 has no partial index, so the books index is a plain one, and as in V13 there
-- is no trigger: on H2 (the test suite only) spent_at is written by the repository's SPENT
-- transitions. The trigger is covered by SpentProofIsFinalIT against real PostgreSQL.

ALTER TABLE t_proof ADD COLUMN keyset_id VARCHAR(66);
ALTER TABLE t_proof ADD COLUMN spent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE t_proof_a ADD COLUMN keyset_id VARCHAR(66);
ALTER TABLE t_proof_a ADD COLUMN spent_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_proof_books_spent ON t_proof (mint_id, keyset_id, spent_at);
