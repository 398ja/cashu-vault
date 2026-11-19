package xyz.tcheeric.cashu.vault.hashi.config;

import java.util.Optional;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * A {@link VaultTemplate} for login-based authentication (AppRole, Kubernetes): its token is
 * renewed in the background and replaced by a fresh login when it can no longer be renewed.
 *
 * <p>{@link #withRelogin(Supplier)} runs a Vault call and, if Vault answers 403, logs in again
 * once and retries. That covers the gap a background renewal cannot: a token that died between
 * two renewals (max TTL reached, revoked by an operator, or lost by a Vault restart).
 *
 * <p>Token authentication does not use this class; it keeps the plain {@code VaultTemplate}.
 */
@Slf4j
public class RenewingVaultTemplate extends VaultTemplate {

    private final RenewingSessionManager sessionManager;
    private final ThreadPoolTaskScheduler scheduler;

    private RenewingVaultTemplate(final VaultEndpoint endpoint, final RenewingSessionManager sessionManager,
                                  final ThreadPoolTaskScheduler scheduler) {
        super(endpoint, new SimpleClientHttpRequestFactory(), sessionManager);
        this.sessionManager = sessionManager;
        this.scheduler = scheduler;
    }

    public static RenewingVaultTemplate create(final VaultEndpoint endpoint,
                                               final ClientAuthentication authentication) {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("vault-session-");
        scheduler.setDaemon(true);
        scheduler.setPoolSize(1);
        scheduler.initialize();
        final RenewingSessionManager manager = new RenewingSessionManager(authentication, scheduler,
                VaultClients.createRestTemplate(endpoint, new SimpleClientHttpRequestFactory()));
        return new RenewingVaultTemplate(endpoint, manager, scheduler);
    }

    /** Runs {@code call}; on a 403 forgets the token, logs in again and retries once. */
    public <T> T withRelogin(final Supplier<T> call) {
        try {
            return call.get();
        } catch (final RuntimeException e) {
            if (!isForbidden(e)) {
                throw e;
            }
            log.warn("Vault answered 403; logging in again and retrying once");
            sessionManager.invalidate();
            return call.get();
        }
    }

    static boolean isForbidden(final Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpStatusCodeException h && h.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                return true;
            }
            final String message = Optional.ofNullable(t.getMessage()).orElse("");
            if (message.contains("Status 403") || message.contains("403 Forbidden")) {
                return true;
            }
        }
        return false;
    }

    RenewingSessionManager sessionManager() {
        return sessionManager;
    }

    @Override
    public void destroy() throws Exception {
        try {
            super.destroy();
        } finally {
            // Revokes the login token (best effort) and stops the renewal thread.
            sessionManager.destroy();
            scheduler.shutdown();
        }
    }
}
