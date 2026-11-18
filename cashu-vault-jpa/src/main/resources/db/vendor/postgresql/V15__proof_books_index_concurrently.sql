-- cashu-vault#156: the books index, built without blocking proof writes.
--
-- Alone in its file because CREATE INDEX CONCURRENTLY cannot run inside a transaction; Flyway
-- runs such a statement outside one. If a build is interrupted it leaves an INVALID index, so
-- drop any leftover first and the migration can simply be re-run.

DROP INDEX CONCURRENTLY IF EXISTS idx_proof_books_spent;

CREATE INDEX CONCURRENTLY idx_proof_books_spent
    ON t_proof (mint_id, keyset_id, spent_at)
    INCLUDE (amount)
    WHERE state = 'SPENT';
