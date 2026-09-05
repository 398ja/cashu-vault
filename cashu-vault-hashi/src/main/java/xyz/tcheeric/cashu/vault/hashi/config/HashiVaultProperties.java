package xyz.tcheeric.cashu.vault.hashi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault.hashi")
@Data
public class HashiVaultProperties {

    /**
     * Master switch for the HashiCorp Vault backend. When {@code false} (the
     * default), {@code HashiVaultConfig}, {@code HashiVaultRegistrar}, and
     * {@code HashiVaultClient} skip wiring entirely, letting the JPA service
     * fall back to the DB-backed {@code VaultClientFactory} clients. Set to
     * {@code true} in deployments where a HashiCorp Vault is reachable and
     * configured via the {@code vault.hashi.uri} / {@code auth.*} properties
     * below; test profiles (which lack a Vault instance) leave it off so the
     * Spring context boots.
     */
    private boolean enabled = false;

    private String uri = "https://vault.internal:8200";

    private Auth auth = new Auth();

    private Engine engine = new Engine();

    @Data
    public static class Auth {
        private String method = "token";
        private String token;
        private AppRole approle = new AppRole();
        private Kubernetes kubernetes = new Kubernetes();
    }

    @Data
    public static class AppRole {
        private String roleId;
        private String secretId;
        /**
         * A response-wrapping token to unwrap into the secret-id, instead of the secret-id itself.
         *
         * <p>The provisioning job creates the secret-id with {@code -wrap-ttl}, so what it can
         * safely hand over is a single-use wrapping token rather than the credential. Nothing
         * consumed that: the application only read {@code secret-id}, so the wrapped value the
         * job produced had no supported path into the configuration and expired ten minutes
         * later. Set this instead of {@code secret-id} to complete that flow.
         */
        private String wrappedSecretId;
        private String path = "approle";
    }

    @Data
    public static class Kubernetes {
        private String role;
        private String tokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";
        private String path = "kubernetes";
    }

    @Data
    public static class Engine {
        private String mount = "cashu";
    }
}
