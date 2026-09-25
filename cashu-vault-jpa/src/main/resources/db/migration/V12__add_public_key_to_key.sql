-- Store the public key alongside the vault reference, so publishing a keyset needs no secret read.
--
-- Issue #146. Loading one mint cost one HTTP round trip per key: the batch endpoint
-- GET /vault/key/keyset/{id} already returned every KeyEntity, but privateKey is @Transient and
-- so absent from that response, and the only reason the caller wanted it was to call
-- derivePublicKey and discard the private key. Measured on staging over one anchored window of
-- six swaps: 424 GET /vault/key/... for 58 distinct key ids, a 7.3x repeat, each one triggering
-- a HashiCorp read.
--
-- The public key is not secret and derivation from the private key is deterministic, so the
-- derived value can simply be stored. Nothing about the private key changes: it stays in
-- HashiCorp Vault, referenced by vault_path, and is still never persisted here.
--
-- NULL is allowed because rows written before this migration have no derived value yet and one
-- cannot be computed in SQL: the private key it derives from lives in HashiCorp Vault, not in
-- this database. KeyPublicKeyBackfill fills them in once at startup, and DBKeySetVault.load
-- falls back to a per-key read for any row still carrying NULL.
--
-- 66 characters is a compressed secp256k1 point in hex (33 bytes), which is what
-- PublicKey.toString produces and what NUT-01 publishes.
ALTER TABLE t_key ADD COLUMN public_key VARCHAR(66);

ALTER TABLE t_key_a ADD COLUMN public_key VARCHAR(66);
