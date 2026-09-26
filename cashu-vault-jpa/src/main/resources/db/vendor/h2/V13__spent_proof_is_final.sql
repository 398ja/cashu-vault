-- H2 counterpart of the PostgreSQL migration of the same version (cashu-vault#154).
--
-- The PostgreSQL migration installs a trigger that refuses to delete a SPENT proof, move it out
-- of SPENT, or change its identity. H2 triggers are Java classes rather than SQL, and H2 is used
-- only by the test suite, so there is no H2 trigger. The invariant is enforced for H2 by the API
-- alone (insert-only store, no proof DELETE), and the trigger itself is covered by
-- SpentProofIsFinalIT against real PostgreSQL.
--
-- The file exists so both engines record the same schema version.

SELECT 1;
