-- Adds vault_path reference column and makes private_key nullable for HashiCorp Vault migration.

ALTER TABLE t_key ADD COLUMN vault_path VARCHAR(512);
ALTER TABLE t_key ALTER COLUMN private_key DROP NOT NULL;

ALTER TABLE t_key_a ADD COLUMN vault_path VARCHAR(512);

CREATE INDEX idx_key_vault_path ON t_key (vault_path);
