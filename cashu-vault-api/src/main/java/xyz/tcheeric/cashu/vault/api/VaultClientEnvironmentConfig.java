package xyz.tcheeric.cashu.vault.api;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;

/**
 * Hands the Spring {@link Environment} to {@link VaultClient}.
 *
 * <p>{@code VaultClient} instances are created statically per entity type, so they cannot receive
 * an injected {@code Environment} the ordinary way. Without this the client resolved its API token
 * only from {@code System.getenv} and {@code System.getProperty}, so a deployment that set
 * {@code vault.api.token} in {@code application.properties} or through a config server, which is
 * the convention the vault service itself uses, got a client that presented no credential and
 * 401ed on every call while the token sat visibly in configuration.
 *
 * <p>Lives in {@code xyz.tcheeric.cashu.vault.api} because that package is already component
 * scanned by the consuming applications; no consumer has to change its scan configuration.
 */
@Configuration
public class VaultClientEnvironmentConfig implements InitializingBean {

    private final Environment environment;

    public VaultClientEnvironmentConfig(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        VaultClient.setSpringEnvironment(environment);
    }
}
