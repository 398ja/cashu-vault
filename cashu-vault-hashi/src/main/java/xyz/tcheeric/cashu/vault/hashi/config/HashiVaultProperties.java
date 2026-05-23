package xyz.tcheeric.cashu.vault.hashi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault.hashi")
@Data
public class HashiVaultProperties {

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
