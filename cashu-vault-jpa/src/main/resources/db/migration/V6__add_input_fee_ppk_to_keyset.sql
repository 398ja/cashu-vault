-- NUT-02 prices a transaction by the input_fee_ppk of the keyset that issued
-- each input. The admin decides that fee and the mint charges it, and the two
-- share only this vault, so the fee belongs on the keyset row next to the id
-- and unit that already travel this way.
--
-- It is part of what a keyset *is*, not configuration hung beside it: under
-- keyset id v2 the fee is an input to the id preimage, so a different fee is a
-- different keyset. Storing it here keeps that true when v2 arrives.
--
-- DEFAULT 0 is what keeps this safe to apply to a running mint: every existing
-- keyset keeps charging nothing, so the migration alone changes no behaviour.
-- A mint charges a fee only once an operator sets one.

ALTER TABLE t_keyset
    ADD COLUMN input_fee_ppk INTEGER NOT NULL DEFAULT 0;

-- The audit table records history and must tolerate rows written before the
-- column existed, so it is nullable there even though it is NOT NULL above.
ALTER TABLE t_keyset_a
    ADD COLUMN input_fee_ppk INTEGER;
