package xyz.tcheeric.cashu.vault.hashi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.event.AfterLoginEvent;
import org.springframework.vault.authentication.event.AfterLoginTokenRenewedEvent;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import xyz.tcheeric.cashu.vault.hashi.client.HashiVaultClient;

/**
 * A login token (AppRole) has a TTL. With the plain {@code VaultTemplate} the first token was used
 * until it expired, then every call answered 403. Against a real Vault with a 10 s token TTL and a
 * 30 s max TTL, the client must renew, log in again after the max TTL, and recover from a Vault
 * restart that lost every token, with no failed call outside the restart window.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("AppRole session renewal against a real Vault")
class AppRoleSessionRenewalIT {

    private static final String ROOT = "renewal-it-root";
    private static final String ROLE_ID = "renewal-it-role-id";
    private static final String SECRET_ID = "renewal-it-secret-id";

    private int port;
    private GenericContainer<?> vault;
    private VaultTemplate template;

    @BeforeEach
    void start() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        // A fixed host port, so the client keeps its address across a container restart.
        vault = new GenericContainer<>("hashicorp/vault:1.18")
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT)
                .withExposedPorts(8200)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(8200))))
                .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200));
        vault.start();
        provision();
    }

    @AfterEach
    void stop() throws Exception {
        if (template != null) {
            ((RenewingVaultTemplate) template).destroy();
        }
        if (vault != null) {
            vault.stop();
        }
    }

    /** A dev Vault keeps nothing across a restart, so the same role and secret-id are re-created. */
    private void provision() throws Exception {
        waitForVault();
        exec("vault secrets enable -path=cashu kv-v2");
        exec("printf 'path \"cashu/data/keys/*\" { capabilities = [\"create\",\"read\",\"update\"] }\\n'"
                + " | vault policy write mint-signer -");
        exec("vault auth enable approle");
        exec("vault write auth/approle/role/mint token_policies=mint-signer token_ttl=10s token_max_ttl=30s"
                + " token_type=service");
        exec("vault write auth/approle/role/mint/role-id role_id=" + ROLE_ID);
        exec("vault write auth/approle/role/mint/custom-secret-id secret_id=" + SECRET_ID);
        exec("vault kv put -mount=cashu keys/m/k/1 private_key=abc");
    }

    private void waitForVault() throws Exception {
        for (int i = 0; i < 60; i++) {
            if (vault.execInContainer("sh", "-c", "VAULT_ADDR=http://127.0.0.1:8200 vault status")
                    .getExitCode() == 0) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("vault did not come up");
    }

    private void exec(final String command) throws Exception {
        final var r = vault.execInContainer("sh", "-c",
                "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=" + ROOT + "; " + command);
        assertThat(r.getExitCode()).as(command + ": " + r.getStderr()).isZero();
    }

    private HashiVaultClient client(final AtomicInteger logins, final AtomicInteger renewals) {
        final HashiVaultProperties properties = new HashiVaultProperties();
        properties.setUri("http://localhost:" + port);
        properties.getAuth().setMethod("approle");
        properties.getAuth().getApprole().setRoleId(ROLE_ID);
        properties.getAuth().getApprole().setSecretId(SECRET_ID);
        final HashiVaultConfig config = new HashiVaultConfig();
        final VaultEndpoint endpoint = VaultEndpoint.from(URI.create(properties.getUri()));
        template = config.vaultTemplate(endpoint, config.clientAuthentication(properties, endpoint));
        assertThat(template).isInstanceOf(RenewingVaultTemplate.class);
        ((RenewingVaultTemplate) template).sessionManager().addAuthenticationListener(event -> {
            if (event instanceof AfterLoginTokenRenewedEvent) {
                renewals.incrementAndGet();
            } else if (event instanceof AfterLoginEvent) {
                logins.incrementAndGet();
            }
        });
        return new HashiVaultClient(template, properties);
    }

    private static List<String> readFor(final HashiVaultClient client, final Duration duration) throws Exception {
        final List<String> errors = new ArrayList<>();
        final Instant end = Instant.now().plus(duration);
        while (Instant.now().isBefore(end)) {
            try {
                final Map<String, Object> secret = client.getSecret("cashu/keys/m/k/1");
                if (secret == null || !"abc".equals(secret.get("private_key"))) {
                    errors.add("wrong secret: " + secret);
                }
            } catch (final RuntimeException e) {
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            Thread.sleep(1000);
        }
        return errors;
    }

    @Test
    @DisplayName("renews the token, logs in again after token_max_ttl, and never fails a read")
    void renewsAndReloginsPastMaxTtl() throws Exception {
        final AtomicInteger logins = new AtomicInteger();
        final AtomicInteger renewals = new AtomicInteger();
        final HashiVaultClient client = client(logins, renewals);

        final List<String> errors = readFor(client, Duration.ofSeconds(45));

        assertThat(errors).as("no read may fail across renewal and re-login").isEmpty();
        assertThat(renewals.get()).as("the token was renewed before it expired").isPositive();
        assertThat(logins.get()).as("a new login replaced the token after token_max_ttl (30 s)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("recovers after a Vault restart that lost every token")
    void recoversFromAVaultRestart() throws Exception {
        final AtomicInteger logins = new AtomicInteger();
        final AtomicInteger renewals = new AtomicInteger();
        final HashiVaultClient client = client(logins, renewals);
        assertThat(readFor(client, Duration.ofSeconds(3))).isEmpty();

        final var docker = DockerClientFactory.instance().client();
        docker.restartContainerCmd(vault.getContainerId()).exec();
        provision();

        // The old token no longer exists: the first read gets a 403, logs in again and retries.
        final List<String> errors = readFor(client, Duration.ofSeconds(15));
        assertThat(errors).as("reads after the restart succeed").isEmpty();
        assertThat(logins.get()).as("a fresh login after the restart").isGreaterThanOrEqualTo(2);
    }
}
