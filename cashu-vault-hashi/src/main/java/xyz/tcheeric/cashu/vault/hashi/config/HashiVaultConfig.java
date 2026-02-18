package xyz.tcheeric.cashu.vault.hashi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(HashiVaultProperties.class)
public class HashiVaultConfig {

    @Bean
    public VaultEndpoint vaultEndpoint(HashiVaultProperties properties) {
        return VaultEndpoint.from(URI.create(properties.getUri()));
    }

    @Bean
    public ClientAuthentication clientAuthentication(HashiVaultProperties properties,
                                                     VaultEndpoint vaultEndpoint) {
        return switch (properties.getAuth().getMethod()) {
            case "approle" -> appRoleAuthentication(properties, vaultEndpoint);
            case "kubernetes" -> kubernetesAuthentication(properties, vaultEndpoint);
            default -> new TokenAuthentication(properties.getAuth().getToken());
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
        RestOperations restOperations = new RestTemplate();
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
        RestOperations restOperations = new RestTemplate();
        return new KubernetesAuthentication(options, restOperations);
    }
}
