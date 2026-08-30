-- NUT-02 v2 keyset ids are "01" followed by a 32-byte SHA-256 digest in hex,
-- which is 66 characters. The original columns were sized for the 16-character
-- v1 ids, so provisioning a v2 keyset failed on insert.
ALTER TABLE t_keyset ALTER COLUMN key_set_id TYPE VARCHAR(66);
ALTER TABLE t_keyset_a ALTER COLUMN key_set_id TYPE VARCHAR(66);
