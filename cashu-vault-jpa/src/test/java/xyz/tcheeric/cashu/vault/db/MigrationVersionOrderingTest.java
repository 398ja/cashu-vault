package xyz.tcheeric.cashu.vault.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration versions must be plausible and unique (issue #128).
 *
 * <p>This test used to assert the opposite of what it asserts now, and the reversal is the
 * point. {@code V999__add_nut13_derivation_metadata.sql} shipped with a template placeholder
 * its own header admitted to — "adjust version number based on your migration sequence" — and
 * was never adjusted. Because 999 sorts above every real migration, a database that applied it
 * treated everything authored later as out of order and Flyway refused to start.
 *
 * <p>The response at the time was to require every NEW migration to be numbered above 999,
 * and this test enforced it. That kept deployments booting, but it made the placeholder into
 * policy: the next migration became {@code V1000}, and the one after would have needed 1001.
 * A workaround that each new change must re-apply is not a fix, it is a tax.
 *
 * <p>The files are now {@code V9} and {@code V10}, and {@code PlaceholderMigrationVersionRepair}
 * rewrites deployed history rows to match before Flyway validates. So the high-water rule is
 * obsolete and its inverse is what needs guarding: that no placeholder number comes back.
 *
 * <p>Neither version asserts contiguity. {@code V5} is legitimately absent from this directory
 * because it is engine-specific and lives under {@code db/vendor/{vendor}}; gaps are harmless.
 */
@DisplayName("Flyway migration ordering")
class MigrationVersionOrderingTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.*\\.sql$");

    /**
     * Anything beyond this is a placeholder or an escape hatch, not a version. Chosen with room
     * to spare: the repo is at 10, so a genuine jump to 100 would be extraordinary.
     */
    private static final long IMPLAUSIBLE_VERSION = 100;

    @Test
    @DisplayName("no migration carries a placeholder version")
    void noMigrationCarriesAPlaceholderVersion() throws Exception {
        List<Path> migrations = migrationFiles();
        assertThat(migrations).as("migrations should be discoverable").isNotEmpty();

        List<String> placeholders = new ArrayList<>();
        for (Path migration : migrations) {
            Matcher matcher = VERSIONED.matcher(migration.getFileName().toString());
            if (matcher.matches() && Long.parseLong(matcher.group(1)) >= IMPLAUSIBLE_VERSION) {
                placeholders.add(migration.getFileName().toString());
            }
        }

        assertThat(placeholders)
                .as("a version at or above %d is a placeholder, not a version: it sorts above "
                        + "every real migration, so each later one appears out of order and "
                        + "Flyway refuses to start against a database that applied it (#128)",
                        IMPLAUSIBLE_VERSION)
                .isEmpty();
    }

    @Test
    @DisplayName("the old placeholder filenames do not come back")
    void theOldPlaceholderFilenamesAreGone() throws Exception {
        // Named explicitly as well as caught by the rule above, so the failure says what went
        // wrong rather than leaving someone to infer it from a number. Reintroducing either
        // file would also desynchronise PlaceholderMigrationVersionRepair, which rewrites
        // deployed history rows from 999 to 9 and from 1000 to 10.
        List<String> names = migrationFiles().stream()
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(names)
                .as("V999 must stay renumbered to V9")
                .doesNotContain("V999__add_nut13_derivation_metadata.sql");
        assertThat(names)
                .as("V1000 must stay renumbered to V10")
                .doesNotContain("V1000__redact_proof_audit_material.sql");
    }

    @Test
    @DisplayName("migration versions are unique")
    void versionsAreUnique() throws Exception {
        // Two files with the same version make Flyway fail outright, and the duplicate is easy to
        // create when renumbering.
        List<Long> versions = migrationFiles().stream()
                .map(path -> VERSIONED.matcher(path.getFileName().toString()))
                .filter(Matcher::matches)
                .map(matcher -> Long.parseLong(matcher.group(1)))
                .toList();

        assertThat(versions).doesNotHaveDuplicates();
    }

    private static List<Path> migrationFiles() throws IOException, URISyntaxException {
        URL url = MigrationVersionOrderingTest.class.getClassLoader().getResource("db/migration");
        assertThat(url).as("db/migration must be on the test classpath").isNotNull();
        try (Stream<Path> paths = Files.list(Path.of(url.toURI()))) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }
}
