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
 * A new migration must have a higher version than every migration already released, or it will
 * never apply to a database that has one of them.
 *
 * <p>This exists because a migration was added as {@code V9} when {@code V999} was already
 * released and live. Every database that had booted the service was at version 999, so V9 was out
 * of order; {@code spring.flyway.out-of-order} defaults to false and
 * {@code validate-on-migrate} defaults to true, so Flyway would fail with "Detected resolved
 * migration not applied to database: 9" and the application would not start.
 *
 * <p>The failure mode is what makes it worth a test. A fresh database applies 1..9 then 999 and
 * is perfectly happy, so CI is green and every developer machine is green. Only a deployment with
 * real history breaks, which is the one place the migration was supposed to protect.
 *
 * <p>This does not assert contiguity: V5 is legitimately absent, and gaps are harmless. It
 * asserts only that the highest version is the newest one, which is the property Flyway needs.
 */
@DisplayName("Flyway migration ordering")
class MigrationVersionOrderingTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    @DisplayName("no migration is numbered below one that is already released")
    void versionsAreOrderedAgainstTheReleasedHighWaterMark() throws Exception {
        List<Path> migrations = migrationFiles();
        assertThat(migrations).as("migrations should be discoverable").isNotEmpty();

        // V999 was released with live ALTER statements, so any deployment that has booted is at
        // least at 999. Anything numbered at or below that and not already applied is unusable.
        long releasedHighWaterMark = 999;

        List<String> outOfOrder = new ArrayList<>();
        for (Path migration : migrations) {
            Matcher matcher = VERSIONED.matcher(migration.getFileName().toString());
            if (!matcher.matches()) {
                continue;
            }
            long version = Long.parseLong(matcher.group(1));
            if (version > releasedHighWaterMark) {
                continue; // newer than everything released: fine
            }
            if (isAlreadyReleased(version)) {
                continue; // part of the released history itself
            }
            outOfOrder.add(migration.getFileName().toString());
        }

        assertThat(outOfOrder)
                .as("these are numbered at or below the released V999, so Flyway will refuse to "
                        + "start on any existing database; renumber above %d",
                        releasedHighWaterMark)
                .isEmpty();
    }

    /** Versions that shipped before V999 and are therefore already recorded in real databases. */
    private static boolean isAlreadyReleased(long version) {
        return version <= 8 || version == 999;
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
