package xyz.tcheeric.cashu.vault.hashi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authenticating the application to Vault with a root token makes every least-privilege policy on
 * the mount decorative, so the deployment says {@code approle} and expects to get it.
 *
 * <p>The switch matched the exact lowercase literal and its {@code default} branch built a
 * {@code TokenAuthentication} from {@code vault.hashi.auth.token}. So
 * {@code VAULT_HASHI_AUTH_METHOD=APPROLE}, which is what docker-compose.prod.yml actually set,
 * fell through to token authentication with a null token. The deployment asked for AppRole, was
 * given something else, and learned about it from a failed Vault call rather than a startup error.
 * Any typo behaved identically.
 */
@DisplayName("Vault authentication method selection")
class HashiVaultConfigAuthMethodTest {

    private final HashiVaultConfig config = new HashiVaultConfig();
    private final VaultEndpoint endpoint = VaultEndpoint.from(URI.create("http://vault:8200"));

    @Test
    @DisplayName("approle is selected case-insensitively")
    void approleIsCaseInsensitive() {
        for (String spelling : new String[]{"approle", "APPROLE", "AppRole", " approle "}) {
            ClientAuthentication auth = config.clientAuthentication(properties(spelling), endpoint);

            assertThat(auth)
                    .as("%s must mean AppRole, not a silent fallback to token auth", spelling)
                    .isInstanceOf(AppRoleAuthentication.class);
        }
    }

    @Test
    @DisplayName("an unrecognised method is refused rather than defaulted to token auth")
    void unrecognisedMethodIsRefused() {
        assertThatThrownBy(() -> config.clientAuthentication(properties("approl"), endpoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecognised");
    }

    @Test
    @DisplayName("token auth must be asked for by name and needs a token")
    void tokenAuthIsExplicit() {
        HashiVaultProperties withToken = properties("token");
        withToken.getAuth().setToken("s.aaaaaaaaaaaaaaaaaaaa");

        assertThat(config.clientAuthentication(withToken, endpoint))
                .isInstanceOf(TokenAuthentication.class);

        // The old default branch built exactly this with a null token and no complaint.
        assertThatThrownBy(() -> config.clientAuthentication(properties("token"), endpoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token is not set");
    }

    @Test
    @DisplayName("a null or blank method is refused")
    void nullMethodIsRefused() {
        assertThatThrownBy(() -> config.clientAuthentication(properties(null), endpoint))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.clientAuthentication(properties("  "), endpoint))
                .isInstanceOf(IllegalStateException.class);
    }

    private static HashiVaultProperties properties(String method) {
        HashiVaultProperties properties = new HashiVaultProperties();
        properties.getAuth().setMethod(method);
        properties.getAuth().getApprole().setRoleId("11111111-1111-1111-1111-111111111111");
        properties.getAuth().getApprole().setSecretId("22222222-2222-2222-2222-222222222222");
        return properties;
    }
}
