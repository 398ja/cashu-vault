package xyz.tcheeric.cashu.vault.hashi.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Reads the Kubernetes service account token from the mounted file.
 */
class KubernetesServiceAccountTokenFile implements Supplier<String> {

    private final String tokenPath;

    KubernetesServiceAccountTokenFile(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    @Override
    public String get() {
        try {
            return Files.readString(Path.of(tokenPath)).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Kubernetes service account token from " + tokenPath, e);
        }
    }
}
