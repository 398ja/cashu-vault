package xyz.tcheeric.cashu.vault.hashi.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;
import xyz.tcheeric.cashu.vault.hashi.config.HashiVaultProperties;

import java.util.Map;

@Component
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
        VaultVersionedKeyValueOperations ops = vaultTemplate
                .opsForVersionedKeyValue(properties.getEngine().getMount());

        // Read current version for CAS (check-and-set) support
        Versioned<Map<String, Object>> existing = ops.get(path);
        Version version = (existing != null && existing.getVersion() != null)
                ? existing.getVersion()
                : Version.unversioned();

        ops.put(path, Versioned.create(data, version));
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
        VaultVersionedKeyValueOperations ops = vaultTemplate
                .opsForVersionedKeyValue(properties.getEngine().getMount());
        Versioned<Map<String, Object>> response = ops.get(relativePath);
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
        VaultVersionedKeyValueOperations ops = vaultTemplate
                .opsForVersionedKeyValue(properties.getEngine().getMount());
        ops.delete(relativePath);
    }

    /**
     * Returns the configured mount path for the secrets engine.
     */
    public String getMount() {
        return properties.getEngine().getMount();
    }

    private String stripMount(String fullPath) {
        String mount = properties.getEngine().getMount() + "/";
        return fullPath.startsWith(mount) ? fullPath.substring(mount.length()) : fullPath;
    }
}
