package xyz.tcheeric.cashu.vault.api;

import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.config.VaultBaseProperties;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple factory providing singleton instances of {@link VaultClient}
 * implementations configured via {@link VaultBaseProperties}.
 */
public final class VaultClientFactory {

    private static final VaultBaseProperties PROPERTIES = new VaultBaseProperties();
    private static final Map<Class<?>, VaultClient<?>> CLIENTS = new ConcurrentHashMap<>();

    private static final KeySetVaultClient KEY_SET_CLIENT;
    private static final KeyVaultClient KEY_CLIENT;
    private static final ProofClient PROOF_CLIENT;

    static {
        KEY_SET_CLIENT = new KeySetVaultClient();
        KEY_SET_CLIENT.setBaseUrl(PROPERTIES.getUrl());
        KEY_CLIENT = new KeyVaultClient();
        KEY_CLIENT.setBaseUrl(PROPERTIES.getUrl());
        PROOF_CLIENT = new ProofClient();
        PROOF_CLIENT.setBaseUrl(PROPERTIES.getUrl());
    }

    private VaultClientFactory() {
    }

    /**
     * Returns a singleton {@link VaultClient} for the given entity type.
     *
     * @param type entity class
     * @param <T>  entity type
     * @return configured vault client
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseEntity> VaultClient<T> getClient(Class<T> type) {
        return (VaultClient<T>) CLIENTS.computeIfAbsent(type, t -> new VaultClient<>(t, PROPERTIES.getUrl()));
    }

    /**
     * Returns a singleton {@link KeySetVaultClient} instance.
     *
     * @return configured key set vault client
     */
    public static KeySetVaultClient keySetClient() {
        return KEY_SET_CLIENT;
    }

    /**
     * Returns a singleton {@link KeyVaultClient} instance.
     *
     * @return configured key vault client
     */
    public static KeyVaultClient keyClient() {
        return KEY_CLIENT;
    }

    /**
     * Returns a singleton {@link ProofClient} instance.
     *
     * @return configured proof client
     */
    public static ProofClient proofClient() {
        return PROOF_CLIENT;
    }
}
