-- cashu-vault#156: record which keyset signed each proof and when it was spent.
--
-- The books reconciler (cashu-mint#515) proves spent(k) <= signed(k) per keyset, and needs the
-- vault to say how much was spent, on which keyset, up to a watermark T. t_proof could answer
-- none of that: it had no keyset id, and the bulk JPQL updates that mark proofs spent set neither
-- updated_at nor an Envers revision, so there was no reliable spend time either.
--
-- Both columns are nullable. Legacy rows keep keyset_id NULL and are reported as "unattributed"
-- spend. They are never back-filled by guess: a guessed keyset would let a forged keyset hide.
--
-- Engine-specific because of the partial index and the PL/pgSQL trigger; see the H2 file of the
-- same version. V13 is not edited in place (that would change its checksum on every deployed
-- database); the function is replaced here instead.

-- The ALTERs and CREATE TRIGGER take a short ACCESS EXCLUSIVE lock (nullable columns without a
-- default are metadata-only). Fail fast instead of queueing behind a long transaction, which
-- would stall every reader and writer of t_proof behind the waiting lock.
SET LOCAL lock_timeout = '5s';

ALTER TABLE t_proof ADD COLUMN keyset_id VARCHAR(66);
ALTER TABLE t_proof ADD COLUMN spent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE t_proof_a ADD COLUMN keyset_id VARCHAR(66);
ALTER TABLE t_proof_a ADD COLUMN spent_at TIMESTAMP WITH TIME ZONE;

-- The books index is built CONCURRENTLY in V15, outside this transaction: a plain CREATE INDEX
-- holds a SHARE lock on t_proof for the whole build and would block every proof write.

-- The V13 invariant (a SPENT proof is final), extended:
--   * spent_at is owned by the trigger. It is set to now() on the transition into SPENT, by any
--     writer, bulk JPQL updates and raw SQL included, and no caller can forge or omit it.
--   * On a SPENT row, amount, keyset_id and spent_at are frozen alongside state, secret and
--     mint_id. A rewritten amount on a spent row would silently shift the books.
--   * Once set, keyset_id is immutable in every state.
CREATE OR REPLACE FUNCTION t_proof_spent_is_final() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.state = 'SPENT' THEN
            RAISE EXCEPTION 'spent proof is final: % cannot be deleted', OLD.id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.state = 'SPENT' THEN
            NEW.spent_at := now();
        ELSE
            NEW.spent_at := NULL;
        END IF;
        RETURN NEW;
    END IF;

    -- UPDATE
    IF OLD.state = 'SPENT' AND (
            NEW.state IS DISTINCT FROM OLD.state
            OR NEW.secret IS DISTINCT FROM OLD.secret
            OR NEW.mint_id IS DISTINCT FROM OLD.mint_id) THEN
        RAISE EXCEPTION 'spent proof is final: % cannot leave SPENT or change identity', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.state = 'SPENT' AND (
            NEW.amount IS DISTINCT FROM OLD.amount
            OR NEW.keyset_id IS DISTINCT FROM OLD.keyset_id
            OR NEW.spent_at IS DISTINCT FROM OLD.spent_at) THEN
        RAISE EXCEPTION 'spent proof is final: % cannot change amount, keyset or spend time', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.keyset_id IS NOT NULL AND NEW.keyset_id IS DISTINCT FROM OLD.keyset_id THEN
        RAISE EXCEPTION 'proof keyset is immutable: % keeps keyset %', OLD.id, OLD.keyset_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.state <> 'SPENT' THEN
        IF NEW.state = 'SPENT' THEN
            NEW.spent_at := now();
        ELSE
            NEW.spent_at := NULL;
        END IF;
    END IF;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS t_proof_spent_final ON t_proof;
CREATE TRIGGER t_proof_spent_final
    BEFORE INSERT OR UPDATE OR DELETE ON t_proof
    FOR EACH ROW EXECUTE FUNCTION t_proof_spent_is_final();
