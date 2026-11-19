package xyz.tcheeric.cashu.vault.hashi.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;
import xyz.tcheeric.cashu.vault.hashi.config.HashiVaultProperties;
import xyz.tcheeric.cashu.vault.hashi.config.RenewingVaultTemplate;

import java.util.Map;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "vault.hashi", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class HashiVaultClient {

    private final VaultTemplate vaultTemplate;
    private final HashiVaultProperties properties;

    /**
     * Stores a secret at the given path under the configured KV v2 mount.
     *
     * @param path relative path within the secrets engine
     * @param data secret data to store
     * @return full path including mount prefix
     */
    public String storeSecret(String path, Map<String, Object> data) {
        log.info("Storing secret at path: {}/{}", properties.getEngine().getMount(), path);
        withRelogin(() -> {
            VaultVersionedKeyValueOperations ops = kv();
            // Read current version for CAS (check-and-set) support
            Versioned<Map<String, Object>> existing = ops.get(path);
            Version version = (existing != null && existing.getVersion() != null)
                    ? existing.getVersion()
                    : Version.unversioned();
            return ops.put(path, Versioned.create(data, version));
        });
        return properties.getEngine().getMount() + "/" + path;
    }

    /**
     * Retrieves a secret from the given full path.
     *
     * @param fullPath full path including mount prefix
     * @return secret data map, or null if not found
     */
    public Map<String, Object> getSecret(String fullPath) {
        String relativePath = stripMount(fullPath);
        log.info("Retrieving secret from path: {}/{}", properties.getEngine().getMount(), relativePath);
        Versioned<Map<String, Object>> response = withRelogin(() -> kv().get(relativePath));
        return response != null ? response.getData() : null;
    }

    /**
     * Deletes the latest version of a secret at the given path.
     *
     * @param fullPath full path including mount prefix
     */
    public void deleteSecret(String fullPath) {
        String relativePath = stripMount(fullPath);
        log.info("Deleting secret at path: {}/{}", properties.getEngine().getMount(), relativePath);
        withRelogin(() -> {
            kv().delete(relativePath);
            return null;
        });
    }

    /**
     * Returns the configured mount path for the secrets engine.
     */
    public String getMount() {
        return properties.getEngine().getMount();
    }

    private VaultVersionedKeyValueOperations kv() {
        return vaultTemplate.opsForVersionedKeyValue(properties.getEngine().getMount());
    }

    /**
     * A login token (AppRole) can die between two background renewals; on a 403 the renewing
     * template logs in again and retries once. Token auth has nothing to re-login with, so its
     * calls run unchanged.
     */
    private <T> T withRelogin(Supplier<T> call) {
        return vaultTemplate instanceof RenewingVaultTemplate renewing
                ? renewing.withRelogin(call)
                : call.get();
    }

    private String stripMount(String fullPath) {
        String mount = properties.getEngine().getMount() + "/";
        return fullPath.startsWith(mount) ? fullPath.substring(mount.length()) : fullPath;
    }
}
