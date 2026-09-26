-- cashu-vault#154: a SPENT proof is final.
--
-- t_proof is the mint's only record of spent proofs. The mint has no spent table of its own, so
-- it accepts a proof as fresh whenever this table does not say SPENT. Removing a SPENT row, or
-- moving it back to UNSPENT or PENDING, therefore makes a spent proof spendable a second time,
-- and nothing the mint records would show it: the ledger just contains two ordinary spends.
--
-- The API no longer offers a way to do that: store is insert-only and the proof DELETE endpoint
-- is gone. This trigger holds the same line underneath the API, so a leaked token, a future
-- endpoint, or someone with a psql prompt cannot undo it either.
--
-- What stays mutable on a SPENT row is bookkeeping only (archived, updated_at, version,
-- fingerprint). Its identity (mint_id, secret) and its state are frozen.
--
-- Engine-specific because PL/pgSQL has no H2 equivalent; see the H2 file of the same version.
-- A role that owns the table or is a superuser can still disable triggers, so the application
-- should connect as a role that is neither.

CREATE OR REPLACE FUNCTION t_proof_spent_is_final() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.state = 'SPENT' THEN
        RAISE EXCEPTION 'spent proof is final: % cannot be deleted', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.state = 'SPENT' AND (
            NEW.state IS DISTINCT FROM OLD.state
            OR NEW.secret IS DISTINCT FROM OLD.secret
            OR NEW.mint_id IS DISTINCT FROM OLD.mint_id) THEN
        RAISE EXCEPTION 'spent proof is final: % cannot leave SPENT or change identity', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS t_proof_spent_final ON t_proof;
CREATE TRIGGER t_proof_spent_final
    BEFORE UPDATE OR DELETE ON t_proof
    FOR EACH ROW EXECUTE FUNCTION t_proof_spent_is_final();

-- Row triggers do not fire on TRUNCATE, which would otherwise empty the spent record in one
-- statement, including through TRUNCATE t_mint CASCADE.
CREATE OR REPLACE FUNCTION t_proof_refuse_truncate() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'spent proof is final: t_proof cannot be truncated'
        USING ERRCODE = 'integrity_constraint_violation';
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS t_proof_no_truncate ON t_proof;
CREATE TRIGGER t_proof_no_truncate
    BEFORE TRUNCATE ON t_proof
    FOR EACH STATEMENT EXECUTE FUNCTION t_proof_refuse_truncate();
