package xyz.tcheeric.cashu.vault.db.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vault serves spendable proof secrets and keyset material, so the client has to present a
 * bearer token. Where it looks for that token decides whether a correctly configured deployment
 * works or 401s on every call.
 *
 * <p>The client originally read only {@code System.getenv("VAULT_API_TOKEN")} and
 * {@code System.getProperty("vault.api.token")}. The vault service configures itself with
 * {@code vault.api.token=${VAULT_API_TOKEN:}} in {@code application.properties}, so a deployment
 * following the same convention on the mint side set a property the client never read: it logged a
 * warning and failed every request with the token visibly present in configuration. Worse, the
 * token was captured once at construction, and the client is constructed statically per entity
 * type, so it could easily be read before Spring had published anything at all.
 */
@DisplayName("Vault API token resolution")
class VaultClientTokenResolutionTest {

    @AfterEach
    void clearEnvironment() {
        VaultClient.setSpringEnvironment(null);
        System.clearProperty("vault.api.token");
    }

    @Test
    @DisplayName("the token is read from the Spring Environment")
    void tokenIsReadFromSpringEnvironment() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("vault.api.token", "from-application-properties");
        VaultClient.setSpringEnvironment(environment);

        assertThat(loadApiToken())
                .as("application.properties is how this codebase configures the vault side; the "
                        + "client ignoring it meant a configured deployment 401ed on every call")
                .isEqualTo("from-application-properties");
    }

    @Test
    @DisplayName("a system property still works without a Spring context")
    void systemPropertyStillWorks() throws Exception {
        System.setProperty("vault.api.token", "from-system-property");

        assertThat(loadApiToken()).isEqualTo("from-system-property");
    }

    @Test
    @DisplayName("an Environment without the property falls through to the other sources")
    void environmentWithoutPropertyFallsThrough() throws Exception {
        VaultClient.setSpringEnvironment(new MockEnvironment());
        System.setProperty("vault.api.token", "from-system-property");

        assertThat(loadApiToken())
                .as("publishing an Environment must not shadow the sources that worked before")
                .isEqualTo("from-system-property");
    }

    @Test
    @DisplayName("a blank Environment value is treated as unset")
    void blankEnvironmentValueIsIgnored() throws Exception {
        // vault.api.token=${VAULT_API_TOKEN:} resolves to empty when the variable is unset, which
        // must not be mistaken for a configured credential.
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("vault.api.token", "   ");
        VaultClient.setSpringEnvironment(environment);
        System.setProperty("vault.api.token", "from-system-property");

        assertThat(loadApiToken()).isEqualTo("from-system-property");
    }

    @Test
    @DisplayName("no token anywhere resolves to null, so the client fails closed")
    void noTokenAnywhereIsNull() throws Exception {
        assertThat(loadApiToken())
                .as("sending no header is right: the alternative is a client that silently works "
                        + "against an unsecured vault")
                .isNull();
    }

    @Test
    @DisplayName("an Environment published after construction is still honoured")
    void environmentPublishedAfterConstructionIsHonoured() throws Exception {
        // The ordering that made capture-at-construction wrong: clients are static per entity
        // type, so they exist before the context does.
        assertThat(loadApiToken()).isNull();

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("vault.api.token", "published-late");
        VaultClient.setSpringEnvironment(environment);

        assertThat(loadApiToken()).isEqualTo("published-late");
    }

    private static String loadApiToken() throws Exception {
        Method method = VaultClient.class.getDeclaredMethod("loadApiToken");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }
}
