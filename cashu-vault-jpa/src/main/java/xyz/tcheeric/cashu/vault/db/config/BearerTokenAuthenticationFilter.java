package xyz.tcheeric.cashu.vault.db.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates a request carrying {@code Authorization: Bearer <token>} against the configured
 * vault API tokens, and grants the role that token carries.
 *
 * <p>Two tokens exist (cashu-vault#154). The <em>client</em> token is the mint's: it may read and
 * may perform the narrow writes the mint needs. The optional <em>reader</em> token may only read,
 * so a dashboard or an operator script can be given access without also being given the power to
 * change proof state.
 *
 * <p>The comparison is {@link MessageDigest#isEqual} over raw bytes, not {@code String.equals}.
 * A short-circuiting comparison leaks the length of the matching prefix through timing, which is
 * enough to recover a static token byte by byte given enough requests. The token bytes are also
 * hashed to a fixed length first, so the comparison cost does not vary with the length of the
 * attacker's guess either. Every configured token is compared on every request, so the time
 * taken does not reveal which of them matched.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Role of the mint's token: read plus the proof-state writes the mint performs. */
    public static final String ROLE_CLIENT = "VAULT_CLIENT";

    /** Role of the read-only token. */
    public static final String ROLE_READER = "VAULT_READER";

    private static final String BEARER = "Bearer ";

    private final List<Credential> credentials;

    /**
     * @param clientToken the mint's token; blank disables it
     * @param readerToken the read-only token; blank disables it
     */
    public BearerTokenAuthenticationFilter(String clientToken, String readerToken) {
        this.credentials = List.of(
                Credential.of(clientToken, ROLE_CLIENT),
                Credential.of(readerToken, ROLE_READER));
    }

    /** A filter with only the mint's token configured. */
    public BearerTokenAuthenticationFilter(String clientToken) {
        this(clientToken, null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            byte[] presented = sha256(header.substring(BEARER.length()).trim());
            roleFor(presented).ifPresent(BearerTokenAuthenticationFilter::authenticateAs);
        }
        // No token, or a bad one, leaves the context anonymous and the authorization rules
        // reject it. Deliberately not short-circuiting with a 401 here: that is the entry
        // point's job, and it keeps the permitAll() rule for health probes working.
        filterChain.doFilter(request, response);
    }

    private Optional<String> roleFor(byte[] presentedDigest) {
        String matched = null;
        for (Credential credential : credentials) {
            if (credential.matches(presentedDigest) && matched == null) {
                matched = credential.role();
            }
        }
        return Optional.ofNullable(matched);
    }

    private static void authenticateAs(String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "vault-" + role.toLowerCase(), null,
                AuthorityUtils.createAuthorityList("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** One configured token and the role it grants. An unconfigured token matches nothing. */
    private record Credential(byte[] digest, boolean configured, String role) {

        static Credential of(String token, String role) {
            boolean configured = token != null && !token.isBlank();
            return new Credential(configured ? sha256(token) : new byte[32], configured, role);
        }

        boolean matches(byte[] presentedDigest) {
            // isEqual runs first so an unconfigured slot costs the same time as a configured one.
            return MessageDigest.isEqual(digest, presentedDigest) && configured;
        }
    }
}
