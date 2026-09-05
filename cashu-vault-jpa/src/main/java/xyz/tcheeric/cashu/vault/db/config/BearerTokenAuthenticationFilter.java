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

/**
 * Authenticates a request carrying {@code Authorization: Bearer <token>} against the configured
 * vault API token.
 *
 * <p>The comparison is {@link MessageDigest#isEqual} over raw bytes, not {@code String.equals}.
 * A short-circuiting comparison leaks the length of the matching prefix through timing, which is
 * enough to recover a static token byte by byte given enough requests. The token bytes are also
 * hashed to a fixed length first, so the comparison cost does not vary with the length of the
 * attacker's guess either.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final byte[] expectedDigest;
    private final boolean configured;

    public BearerTokenAuthenticationFilter(String expectedToken) {
        this.configured = expectedToken != null && !expectedToken.isBlank();
        this.expectedDigest = configured ? sha256(expectedToken) : new byte[32];
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (configured && header != null && header.startsWith(BEARER)) {
            String presented = header.substring(BEARER.length()).trim();
            if (MessageDigest.isEqual(expectedDigest, sha256(presented))) {
                // One principal: the service holding the token. Roles would be theatre until
                // there is more than one caller.
                var authentication = new UsernamePasswordAuthenticationToken(
                        "vault-api-client", null,
                        AuthorityUtils.createAuthorityList("ROLE_VAULT_CLIENT"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        // No token, or a bad one, leaves the context anonymous and the authorization rules
        // reject it. Deliberately not short-circuiting with a 401 here: that is the entry
        // point's job, and it keeps the permitAll() rule for health probes working.
        filterChain.doFilter(request, response);
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
}
