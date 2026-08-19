-- Replace private_key column with vault_path reference to HashiCorp Vault.
-- Private keys are now stored exclusively in HashiCorp Vault.

-- Add vault_path column. A DEFAULT is required so the NOT NULL constraint is satisfiable on tables that
-- already contain rows (pre-existing keys, or dev-seeded data) — without it, ADD COLUMN ... NOT NULL fails
-- with "contains null values". Existing rows get '' as a placeholder path; new rows set it explicitly.
ALTER TABLE t_key ADD COLUMN vault_path VARCHAR(512) NOT NULL DEFAULT '';
CREATE INDEX idx_key_vault_path ON t_key (vault_path);

-- Drop private_key column and its unique index
DROP INDEX IF EXISTS idx_key_private_key_unq;
ALTER TABLE t_key DROP COLUMN private_key;

-- Update audit table
ALTER TABLE t_key_a ADD COLUMN vault_path VARCHAR(512);
ALTER TABLE t_key_a DROP COLUMN private_key;
