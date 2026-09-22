package xyz.tcheeric.cashu.vault.db.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Repairs history rows that recorded the old placeholder migration versions (issue #128).
 *
 * <p>{@code V999__add_nut13_derivation_metadata.sql} shipped with a template placeholder
 * version that was never adjusted — the file said so itself, "adjust version number based on
 * your migration sequence". Because 999 sorts after every real migration, a database that
 * applied it treats everything authored later as out of order and Flyway refuses to start the
 * service:
 *
 * <pre>
 *   Validate failed: Detected resolved migration not applied to database: 3, 4, 6, 7, 8.
 * </pre>
 *
 * <p>Staging boots today only because {@code SPRING_FLYWAY_OUT_OF_ORDER=true} is set. That
 * flag disables the ordering check entirely, so it hides genuinely mis-sequenced migrations in
 * order to accommodate one typo. The files are now {@code V9} and {@code V10}; this brings
 * deployed histories into line with them so the flag is no longer load-bearing.
 *
 * <p><strong>Why a callback and not a migration.</strong> The obvious fix is a {@code V11}
 * that rewrites the rows, and it cannot work: Flyway validates before it migrates, and it is
 * validation that fails. A migration numbered above 999 would be resolved but never executed,
 * because the very condition it exists to repair aborts the run first. Verified against a
 * throwaway Postgres seeded with staging's history — {@code V11} was reported as "not applied"
 * alongside the migrations it was meant to unblock.
 *
 * <p><strong>Why BEFORE_VALIDATE and not BEFORE_MIGRATE.</strong> For the same reason, one step
 * further in. {@code migrate()} validates first and only fires {@code BEFORE_MIGRATE} once
 * validation has passed, so a beforeMigrate callback is equally unreachable here. That was not
 * obvious from the name and was caught by running the real callback against the replica, where
 * it made no difference at all.
 *
 * <p><strong>Checksums are cleared, not rewritten.</strong> Renaming alone would preserve them,
 * but both files also gained comments explaining the renumbering, so their content genuinely
 * changed and the stored checksums no longer match:
 *
 * <pre>
 *   Migration checksum mismatch for migration version 9
 *     -&gt; Applied to database : -500931067
 *     -&gt; Resolved locally    : -301671160
 * </pre>
 *
 * Setting the checksum to NULL makes Flyway skip the comparison for that row, which is what
 * {@code flyway repair} itself does. It is also the honest record: the schema effect of those
 * migrations is unchanged and the new checksum describes a file that did not exist when they
 * ran, so storing it would assert something untrue. Only these two rows are affected; every
 * other migration keeps full checksum validation.
 *
 * <p><strong>Safe on a fresh database.</strong> The history table may not exist yet, and there
 * is nothing to repair when it does not — both statements are guarded on its presence and
 * match nothing on a database that never applied the placeholders. Re-running matches nothing
 * the second time.
 *
 * <p>This is deliberately narrow and self-retiring: it touches two specific rows identified by
 * both version and script name, so a coincidental 999 from some other source is left alone. It
 * can be deleted once no deployment carries the old numbering.
 */
@Slf4j
@Configuration
public class PlaceholderMigrationVersionRepair {

    @Bean
    public Callback placeholderMigrationVersionRepairCallback() {
        return new RepairCallback();
    }

    static final class RepairCallback implements Callback {

        @Override
        public boolean supports(final Event event, final Context context) {
            // BEFORE_VALIDATE, not BEFORE_MIGRATE. Flyway's migrate() validates FIRST and
            // fires BEFORE_MIGRATE only after validation passes, so a beforeMigrate callback
            // never runs on the databases this exists to repair — verified against a replica
            // of staging's history, which still failed with "Detected resolved migration not
            // applied to database: 9, 10". BEFORE_VALIDATE is the first event with a usable
            // connection, and the only point at which the repair is reachable.
            return event == Event.BEFORE_VALIDATE;
        }

        @Override
        public boolean canHandleInTransaction(final Event event, final Context context) {
            return true;
        }

        @Override
        public void handle(final Event event, final Context context) {
            final Connection connection = context.getConnection();
            try (Statement statement = connection.createStatement()) {
                if (!historyTableExists(connection)) {
                    return;
                }
                final int renumbered =
                        renumber(statement, "999", "9", "V999__add_nut13_derivation_metadata.sql")
                                + renumber(statement, "1000", "10",
                                        "V1000__redact_proof_audit_material.sql");
                if (renumbered > 0) {
                    log.info("flyway_placeholder_version_repair rows={} — renumbered the "
                            + "V999/V1000 placeholders so migration order is monotonic (#128)",
                            renumbered);
                }
            } catch (final SQLException e) {
                // Loudly, and fail the start. A half-repaired history is worse than an
                // unrepaired one: the service would come up against a schema whose recorded
                // version no longer describes what actually ran.
                throw new IllegalStateException(
                        "Failed to repair placeholder migration versions (#128)", e);
            }
        }

        /**
         * Asks the driver rather than querying a catalog, so this works on both engines. The
         * service runs on PostgreSQL and the tests on H2, and {@code to_regclass} — the
         * natural guard — exists only on the former.
         */
        private boolean historyTableExists(final Connection connection) throws SQLException {
            for (final String name : new String[] {"flyway_schema_history", "FLYWAY_SCHEMA_HISTORY"}) {
                try (var rs = connection.getMetaData().getTables(null, null, name, null)) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Matches on the script name as well as the version, so only the rows these specific
         * placeholders wrote are touched.
         */
        private int renumber(final Statement statement, final String from, final String to,
                             final String script) throws SQLException {
            // checksum = NULL in the same statement as the version, so a row can never be left
            // renumbered but still carrying a checksum for content it no longer has — which
            // would fail validation on exactly the databases this exists to unblock.
            return statement.executeUpdate(
                    "UPDATE flyway_schema_history SET version = '" + to + "', checksum = NULL"
                            + " WHERE version = '" + from + "'"
                            + " AND script = '" + script + "'");
        }

        @Override
        public String getCallbackName() {
            return "PlaceholderMigrationVersionRepair";
        }
    }
}
