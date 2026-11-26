package xyz.tcheeric.cashu.vault.hashi.config;

import java.util.Optional;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.web.client.RestOperations;

/**
 * Keeps a login-based Vault token (AppRole, Kubernetes) alive for the life of the process.
 *
 * <p>{@code new VaultTemplate(endpoint, authentication)} wraps the authentication in a {@code
 * SimpleSessionManager}, which logs in once and keeps that token forever. That is right for a
 * static token, which is why token auth still uses it. A login token, though, has a TTL: once it
 * passes, every read and write answers 403 until the process restarts. For the mint that means
 * signing fails, hours after a healthy start.
 *
 * <p>This manager renews the token before it expires and, once renewal stops working (the token
 * reached its {@code token_max_ttl}, or Vault forgot it after a restart of a dev-mode server),
 * drops it so the next request logs in again. {@link #invalidate()} lets a caller that got a 403
 * force that re-login immediately rather than waiting for the next renewal.
 */
public class RenewingSessionManager extends LifecycleAwareSessionManager {

  public RenewingSessionManager(
      final ClientAuthentication authentication,
      final TaskScheduler scheduler,
      final RestOperations restOperations) {
    super(authentication, scheduler, restOperations);
    // Any renewal failure (I/O while Vault restarts, 403 after max_ttl) drops the token, so
    // the next request logs in again instead of reusing a dead token.
    setLeaseStrategy(LeaseStrategy.dropOnError());
  }

  /** Forgets the current token without revoking it, so the next request logs in again. */
  public void invalidate() {
    setToken(Optional.empty());
  }
}
