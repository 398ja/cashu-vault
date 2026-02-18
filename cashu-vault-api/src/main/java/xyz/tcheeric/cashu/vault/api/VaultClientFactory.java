package xyz.tcheeric.cashu.vault.api;

import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.config.VaultBaseProperties;
import xyz.tcheeric.cashu.vault.db.model.BaseEntity;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory providing singleton instances of {@link VaultClient} and
 * {@link Vault} implementations. Supports switching between DB and
 * HashiCorp Vault backends.
 */
public final class VaultClientFactory {

    public enum Backend { DB, HASHICORP }

    private static volatile Backend activeBackend = Backend.HASHICORP;

    private static final VaultBaseProperties PROPERTIES = new VaultBaseProperties();
    private static final Map<Class<?>, VaultClient<?>> CLIENTS = new ConcurrentHashMap<>();

    private static final KeySetVaultClient KEY_SET_CLIENT;
    private static final KeyVaultClient KEY_CLIENT;
    private static final ProofClient PROOF_CLIENT;

    private static final Map<Class<?>, Function<?, ? extends Vault<?>>> HC_VAULT_FACTORIES = new ConcurrentHashMap<>();

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
     * Sets the active secrets backend.
     */
    public static void setBackend(Backend backend) {
        activeBackend = backend;
    }

    /**
     * Returns the current active backend.
     */
    public static Backend getBackend() {
        return activeBackend;
    }

    /**
     * Registers a HashiCorp Vault factory for a given entity type.
     * This is called by the cashu-vault-hashi module during initialization.
     *
     * @param type    entity class
     * @param factory function that creates a Vault implementation
     * @param <T>     entity type
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseEntity> void registerHCVault(Class<T> type,
                                                               Function<VaultClient<T>, Vault<T>> factory) {
        HC_VAULT_FACTORIES.put(type, factory);
    }

    /**
     * Returns a {@link Vault} for the given entity type using the active backend.
     *
     * @param type entity class
     * @param <T>  entity type
     * @return vault implementation for the active backend
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseEntity> Vault<T> getVault(Class<T> type) {
        return switch (activeBackend) {
            case DB -> getDBVault(type);
            case HASHICORP -> getHCVault(type);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends BaseEntity> Vault<T> getDBVault(Class<T> type) {
        if (type == KeyEntity.class) {
            return (Vault<T>) new xyz.tcheeric.cashu.vault.api.db.impl.DBKeyVault();
        } else if (type == KeySetEntity.class) {
            return (Vault<T>) new xyz.tcheeric.cashu.vault.api.db.impl.DBKeySetVault();
        } else if (type == ProofEntity.class) {
            return (Vault<T>) new xyz.tcheeric.cashu.vault.api.db.impl.DBProofVault();
        }
        throw new IllegalArgumentException("No DB Vault registered for type: " + type.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends BaseEntity> Vault<T> getHCVault(Class<T> type) {
        Function<VaultClient<T>, Vault<T>> factory =
                (Function<VaultClient<T>, Vault<T>>) (Function<?, ?>) HC_VAULT_FACTORIES.get(type);
        if (factory != null) {
            return factory.apply(getClient(type));
        }
        // Fall back to DB vault for types not registered with HashiCorp (e.g., ProofEntity)
        return getDBVault(type);
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
        return (VaultClient<T>) CLIENTS.computeIfAbsent(type, t -> new VaultClient<>(type, PROPERTIES.getUrl()));
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
