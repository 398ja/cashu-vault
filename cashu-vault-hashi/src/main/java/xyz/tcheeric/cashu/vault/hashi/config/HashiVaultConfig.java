package xyz.tcheeric.cashu.vault.hashi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestOperations;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(HashiVaultProperties.class)
@ConditionalOnProperty(prefix = "vault.hashi", name = "enabled", havingValue = "true")
public class HashiVaultConfig {

    @Bean
    public VaultEndpoint vaultEndpoint(HashiVaultProperties properties) {
        return VaultEndpoint.from(URI.create(properties.getUri()));
    }

    /**
     * Chooses the Vault authentication method.
     *
     * <p>The match is case-insensitive, and an unrecognised value is refused rather than treated
     * as token auth. Previously this switched on the exact lowercase literal with a
     * {@code default} that fell through to {@code TokenAuthentication}, so
     * {@code VAULT_HASHI_AUTH_METHOD=APPROLE} silently became token authentication with a null
     * token: the deployment asked for AppRole, was given something else, and found out from a
     * failed Vault call rather than from a startup error. Any typo behaved the same way.
     *
     * <p>Token auth must now be requested by name, so it is a choice instead of a fallback.
     */
    @Bean
    public ClientAuthentication clientAuthentication(HashiVaultProperties properties,
                                                     VaultEndpoint vaultEndpoint) {
        final String method = properties.getAuth().getMethod();
        final String normalised = method == null ? "" : method.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalised) {
            case "approle" -> appRoleAuthentication(properties, vaultEndpoint);
            case "kubernetes" -> kubernetesAuthentication(properties, vaultEndpoint);
            case "token" -> {
                if (properties.getAuth().getToken() == null
                        || properties.getAuth().getToken().isBlank()) {
                    throw new IllegalStateException(
                            "vault.hashi.auth.method=token but vault.hashi.auth.token is not set");
                }
                yield new TokenAuthentication(properties.getAuth().getToken());
            }
            default -> throw new IllegalStateException(
                    "Unrecognised vault.hashi.auth.method=" + method
                            + ". Use approle, kubernetes or token. An unrecognised method is "
                            + "refused rather than defaulted, because defaulting to token auth "
                            + "with no token silently ignored what the deployment asked for.");
        };
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint vaultEndpoint,
                                       ClientAuthentication clientAuthentication) {
        return new VaultTemplate(vaultEndpoint, clientAuthentication);
    }

    private ClientAuthentication appRoleAuthentication(HashiVaultProperties properties,
                                                       VaultEndpoint vaultEndpoint) {
        HashiVaultProperties.AppRole approle = properties.getAuth().getApprole();
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(approle.getRoleId()))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(approle.getSecretId()))
                .path(approle.getPath())
                .build();
        RestOperations restOperations = VaultClients.createRestTemplate(
                vaultEndpoint, new SimpleClientHttpRequestFactory());
        return new AppRoleAuthentication(options, restOperations);
    }

    private ClientAuthentication kubernetesAuthentication(HashiVaultProperties properties,
                                                          VaultEndpoint vaultEndpoint) {
        HashiVaultProperties.Kubernetes k8s = properties.getAuth().getKubernetes();
        KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .role(k8s.getRole())
                .jwtSupplier(new KubernetesServiceAccountTokenFile(k8s.getTokenPath()))
                .path(k8s.getPath())
                .build();
        RestOperations restOperations = VaultClients.createRestTemplate(
                vaultEndpoint, new SimpleClientHttpRequestFactory());
        return new KubernetesAuthentication(options, restOperations);
    }
}
