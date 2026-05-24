package xyz.tcheeric.cashu.vault.db.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP Basic over a private network, property-driven users.
 * Service accounts carry a MINT:&lt;uuid&gt; GrantedAuthority used by the
 * controller-layer mint-scope cross-check (FR-006). Admin accounts hold ROLE_ADMIN
 * and may operate across any mint (tombstone + audit endpoints).
 *
 * Production credentials MUST be sourced from env / Hashi — never committed.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(VaultSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final VaultSecurityProperties props;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating encoder accepts {noop}, {bcrypt}, etc. — dev defaults to noop,
        // production should use {bcrypt}.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> details = new ArrayList<>();
        for (VaultSecurityProperties.User u : props.getUsers()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String role : u.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            if (u.getMintScope() != null && !u.getMintScope().isBlank()) {
                authorities.add(new SimpleGrantedAuthority("MINT:" + u.getMintScope()));
            }
            String storedPassword = u.getPassword();
            // Tag plaintext properties values for the delegating encoder.
            if (!storedPassword.startsWith("{")) {
                storedPassword = "{noop}" + storedPassword;
            }
            details.add(User.withUsername(u.getUsername())
                    .password(storedPassword)
                    .authorities(authorities)
                    .build());
        }
        return new InMemoryUserDetailsManager(details);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           UserDetailsService userDetailsService,
                                           PasswordEncoder passwordEncoder) throws Exception {
        // Legacy H2-backed integration tests opt out via cashu.vault.security.enabled=false.
        // Production deployments MUST leave this true (default).
        if (!props.isEnabled()) {
            http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        // Legacy global-secret endpoint is a 400 stub — let it through unauthenticated
                        // so callers see the deprecation envelope regardless of credentials.
                        .requestMatchers("/vault/proof/secret/**").permitAll()
                        // Actuator health open for liveness probes; everything else auth-gated.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/vault/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
