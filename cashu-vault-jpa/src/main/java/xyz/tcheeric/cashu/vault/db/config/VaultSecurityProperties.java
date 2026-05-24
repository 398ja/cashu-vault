package xyz.tcheeric.cashu.vault.db.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "cashu.vault.security")
public class VaultSecurityProperties {

    private boolean enabled = true;

    private List<User> users = new ArrayList<>();

    @Data
    public static class User {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();
        private String mintScope;
    }
}
