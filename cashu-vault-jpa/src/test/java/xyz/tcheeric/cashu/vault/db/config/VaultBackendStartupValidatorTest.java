package xyz.tcheeric.cashu.vault.db.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This validator exists because of a live production defect: {@code docker-compose.prod.yml} set
 * {@code VAULT_BACKEND=HASHICORP} but not {@code VAULT_HASHI_ENABLED}, so every class in
 * {@code cashu-vault-hashi} was inactive and the mint stored keyset private keys in Postgres while
 * the configuration said otherwise (audit M-16).
 *
 * <p>The first version of the validator only matched the exact string {@code "hashicorp"}. Any
 * other spelling, {@code HASHI} or {@code VAULT} or a typo, was treated as "not HashiCorp", fell
 * through to a warning, and started the service on database storage: the same silent outcome the
 * validator was written to prevent, reachable through every spelling but one.
 *
 * <p>So an unrecognised value is now refused. The deployment plainly intended something, and
 * guessing which is how the keys ended up in the database the first time.
 */
@DisplayName("Vault backend startup validation")
class VaultBackendStartupValidatorTest {

    @Test
    @DisplayName("an unrecognised backend is refused rather than assumed to be JPA")
    void unrecognisedBackendIsRefused() {
        // Spellings a deployer might plausibly write that are NOT aliases. Each was previously
        // read as "not hashicorp" and started the service on database storage.
        for (String spelling : new String[]{"hashicorpp", "hasicorp", "hashi-corp", "HC",
                "unknown", "none"}) {
            assertThatThrownBy(() -> validate(spelling, false))
                    .as("%s must not silently mean database storage", spelling)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("HASHI with hashi disabled is refused, not warned about")
    void hashiAliasWithBackendDisabledIsRefused() {
        // The exact M-16 shape, in the spelling the old check missed.
        assertThatThrownBy(() -> validate("HASHI", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vault.hashi.enabled");
    }

    @Test
    @DisplayName("hashicorp aliases are accepted when the backend is enabled")
    void hashicorpAliasesAreAccepted() {
        for (String spelling : new String[]{"hashicorp", "HASHICORP", "hashi", "vault",
                "hashicorp-vault", "hashivault", " hashi "}) {
            assertThatCode(() -> validate(spelling, true))
                    .as("%s should be understood as HashiCorp", spelling)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("hashicorp requested but not enabled is refused")
    void hashicorpWithoutEnabledIsRefused() {
        assertThatThrownBy(() -> validate("hashicorp", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stored in the database");
    }

    @Test
    @DisplayName("jpa aliases are accepted")
    void jpaAliasesAreAccepted() {
        for (String spelling : new String[]{"jpa", "JPA", "db", "database", "postgres"}) {
            assertThatCode(() -> validate(spelling, false))
                    .as("%s should be understood as database storage", spelling)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("an unset backend is permitted and only logged")
    void unsetBackendIsPermitted() {
        // Saying nothing is not the same as saying something unrecognised: an empty value is the
        // documented default rather than a possible typo.
        assertThatCode(() -> validate("", false)).doesNotThrowAnyException();
        assertThatCode(() -> validate(null, true)).doesNotThrowAnyException();
        assertThatCode(() -> validate("   ", false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("jpa with hashi enabled starts, since hashi takes precedence")
    void jpaWithHashiEnabledIsPermitted() {
        assertThatCode(() -> validate("jpa", true)).doesNotThrowAnyException();
    }

    private static void validate(String backend, boolean hashiEnabled) {
        VaultBackendStartupValidator validator = new VaultBackendStartupValidator();
        ReflectionTestUtils.setField(validator, "requestedBackend", backend);
        ReflectionTestUtils.setField(validator, "hashiEnabled", hashiEnabled);
        ReflectionTestUtils.invokeMethod(validator, "enforceBackendIsActive");
    }
}
