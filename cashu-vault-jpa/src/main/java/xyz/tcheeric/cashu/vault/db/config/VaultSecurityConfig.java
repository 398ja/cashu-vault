package xyz.tcheeric.cashu.vault.db.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

/**
 * Authentication for the vault API.
 *
 * <h2>What this service holds</h2>
 *
 * <p>{@code t_proof} rows carry {@code secret}, {@code C} and {@code witness}. Those three
 * together <em>are</em> spendable ecash. {@code t_key} rows carry the {@code vault_path} of every
 * mint signing key. Until the 2026-09-05 audit (finding C-2) none of it was protected: there was
 * no security dependency, no filter and no interceptor anywhere in the service, so
 * {@code GET /vault/proof} handed every stored secret to any caller who could open a socket, and
 * {@code DELETE /vault/proof/{id}} let them destroy the double-spend record. The architecture
 * document claimed mTLS; nothing implemented it.
 *
 * <h2>The control</h2>
 *
 * <p>A static bearer token on {@code /vault/**}. This is a service-to-service API with exactly
 * one caller shape (the mint, via {@code VaultClient}), so a shared token that the operator
 * rotates out of band is proportionate; the token is compared in constant time. Deployments that
 * want mTLS or mutual OIDC can replace {@link #vaultSecurityFilterChain} without touching a
 * controller.
 *
 * <p>The token has no default and startup fails without one. A default would be a published
 * credential, and this service holds the funds.
 *
 * <p>Health stays anonymous for container probes. Every other actuator endpoint, and every
 * {@code /vault/**} path, requires the token.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class VaultSecurityConfig {

    private final Environment environment;
    private final String apiToken;

    public VaultSecurityConfig(Environment environment,
                               @Value("${vault.api.token:}") String apiToken) {
        this.environment = environment;
        this.apiToken = apiToken;
    }

    @PostConstruct
    void requireToken() {
        if (apiToken != null && !apiToken.isBlank()) {
            return;
        }
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (testProfile) {
            log.warn("vault.api.token is unset under the test profile; /vault/** will reject "
                    + "every request. Set it in the test context to exercise authenticated paths.");
            return;
        }
        throw new IllegalStateException(
                "vault.api.token (env VAULT_API_TOKEN) is required. This service stores proof "
                        + "secrets and keyset material, which are spendable; it must not start "
                        + "without authentication. Generate one with: openssl rand -hex 32");
    }

    @Bean
    public SecurityFilterChain vaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new BearerTokenAuthenticationFilter(apiToken),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz
                        // Container probes cannot present a credential and expose only UP/DOWN.
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        // Everything else, /vault/** and the remaining actuator endpoints
                        // (metrics, prometheus) alike, needs the token.
                        .anyRequest().authenticated())
                // 401 with a WWW-Authenticate challenge, not 403. The caller is anonymous and
                // the fix is to present a credential; 403 would say "your credential is
                // understood and insufficient", which is a different instruction.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authEx) -> {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"cashu-vault\"");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                }));

        log.info("Vault API authentication enabled on /vault/** (bearer token).");
        return http.build();
    }
}
