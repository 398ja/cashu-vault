package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.vault.api.KeyVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Loading a keyset must cost one call, and must still answer the right keys (issue #146).
 *
 * <p>Both halves are asserted together on purpose. A cheap load that returns nothing, or
 * returns keys the mint cannot sign for, is worse than the round trips it replaced, and an
 * optimisation asserted only by call count would pass while doing exactly that.
 */
class DBKeySetVaultLoadCostTest {

    private static final int KEY_COUNT = 16;

    /**
     * Counts what the load path asks the vault for, so the cost can be asserted rather than
     * inferred. Stands in for the REST client, which is where the round trips originate.
     */
    @Getter
    private static final class CountingKeyVaultClient extends KeyVaultClient {

        private final Set<KeyEntity> keys;
        private int batchCallCount;

        private CountingKeyVaultClient(final Set<KeyEntity> keys) {
            this.keys = keys;
        }

        @Override
        public Set<KeyEntity> getKeysByKeySetId(final String id) {
            batchCallCount++;
            return keys;
        }
    }

    /**
     * Fails any per-key retrieve, which is the round trip this change removes. A counter would
     * let the assertion be written after the fact; refusing outright states the intent.
     */
    private static final class RefusingKeyVault implements KeyVault {

        @Override
        public KeyEntity retrieve(final String id) {
            throw new AssertionError(
                    "A per-key private key read is exactly the round trip issue #146 removed: "
                            + "the batch response already carries the public key. Key id " + id);
        }

        @Override
        public KeyEntity retrieveByAmount(final BigInteger amount, final String keySetId) {
            throw new UnsupportedOperationException("not used by the load path");
        }

        @Override
        public KeyEntity store(final KeyEntity entity) {
            throw new UnsupportedOperationException("not used by the load path");
        }

        @Override
        public KeyEntity archive(final String id) {
            throw new UnsupportedOperationException("not used by the load path");
        }

        @Override
        public void delete(final String id) {
            throw new UnsupportedOperationException("not used by the load path");
        }
    }

    /** Loading a keyset of N keys issues one batch call and no per-key call. */
    @Test
    @DisplayName("loading a keyset costs one call regardless of how many keys it holds")
    void loadIssuesOneCallPerKeySetRatherThanOnePerKey() throws Exception {
        final ProvisionedKeySet provisioned = provisionedKeySet();
        final CountingKeyVaultClient keyClient =
                new CountingKeyVaultClient(provisioned.keys());

        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(new RefusingKeyVault());

            DBKeySetVault.load(provisioned.keySet(), false);
        }

        assertThat(keyClient.getBatchCallCount())
                .as("one batch call per keyset, not one read per key: %d keys were loaded",
                        KEY_COUNT)
                .isEqualTo(1);
    }

    /**
     * The cheap load still answers the correct public key for every amount, so it cannot pass
     * by returning nothing or by returning the wrong keys.
     */
    @Test
    @DisplayName("every amount still maps to its own correctly derived public key")
    void loadReturnsTheCorrectDerivedPublicKeyForEveryAmount() throws Exception {
        final ProvisionedKeySet provisioned = provisionedKeySet();
        final CountingKeyVaultClient keyClient =
                new CountingKeyVaultClient(provisioned.keys());

        final KeySet loaded;
        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(new RefusingKeyVault());

            loaded = DBKeySetVault.load(provisioned.keySet(), false);
        }

        assertThat(loaded.getKeys().getValues())
                .as("a keyset that advertises fewer denominations than it holds is a mint that "
                        + "cannot serve them")
                .hasSize(KEY_COUNT);
        assertThat(loaded.getKeys().getValues())
                .as("each amount must carry the public key derived from its own signing key, "
                        + "or the mint publishes keys it cannot sign with")
                .containsExactlyInAnyOrderEntriesOf(provisioned.expectedKeysByAmount());
    }

    /**
     * A key stored before the public key column existed is still answered correctly, by
     * deriving from its private key. Those rows are backfilled at startup, so this is the
     * transitional path rather than the normal one.
     */
    @Test
    @DisplayName("a key with no stored public key falls back to deriving from the private key")
    void loadDerivesWhenTheStoredPublicKeyIsMissing() throws Exception {
        final PrivateKey privateKey = PrivateKey.generateRandom();
        final PublicKey expected = PrivateKey.derivePublicKey(privateKey);

        final KeySetEntity keySet = emptyKeySet();
        final KeyEntity legacyKey = new KeyEntity();
        legacyKey.setId(UUID.randomUUID());
        legacyKey.setAmount(BigInteger.ONE);
        legacyKey.setKeySet(keySet);

        final KeyEntity resolved = new KeyEntity();
        // Hex-encoded from the bytes, not PrivateKey.toString(), which deliberately answers
        // "PrivateKey(redacted)" so a key cannot leak through a log line or an exception.
        resolved.setPrivateKey(Hex.toHexString(privateKey.getBytes()));

        final CountingKeyVaultClient keyClient = new CountingKeyVaultClient(Set.of(legacyKey));

        final KeySet loaded;
        try (MockedStatic<VaultClientFactory> factory = mockStatic(VaultClientFactory.class)) {
            factory.when(VaultClientFactory::keyClient).thenReturn(keyClient);
            factory.when(VaultClientFactory::keyVault).thenReturn(new KeyVault() {
                @Override
                public KeyEntity retrieve(final String id) {
                    return resolved;
                }

                @Override
                public KeyEntity retrieveByAmount(final BigInteger amount, final String keySetId) {
                    throw new UnsupportedOperationException("not used by the load path");
                }

                @Override
                public KeyEntity store(final KeyEntity entity) {
                    throw new UnsupportedOperationException("not used by the load path");
                }

                @Override
                public KeyEntity archive(final String id) {
                    throw new UnsupportedOperationException("not used by the load path");
                }

                @Override
                public void delete(final String id) {
                    throw new UnsupportedOperationException("not used by the load path");
                }
            });

            loaded = DBKeySetVault.load(keySet, false);
        }

        assertThat(loaded.getKeys().getValues())
                .as("a key provisioned before the public key column must not vanish from the "
                        + "published keyset")
                .containsExactly(Map.entry(BigInteger.ONE, expected));
    }

    /**
     * A keyset of {@link #KEY_COUNT} keys, each carrying the stored public key derived from its
     * own freshly generated signing key, together with the mapping the load must reproduce.
     *
     * <p>The keys are handed back separately rather than attached to the entity because the
     * load path deliberately ignores {@code KeySetEntity.getKeys()}: that relation is
     * {@code @JsonIgnore} and is always empty on an entity that arrived over REST, which is the
     * situation this method is imitating.
     */
    private record ProvisionedKeySet(KeySetEntity keySet,
                                     Set<KeyEntity> keys,
                                     Map<BigInteger, PublicKey> expectedKeysByAmount) {
    }

    private static ProvisionedKeySet provisionedKeySet() {
        final KeySetEntity keySet = emptyKeySet();
        final Set<KeyEntity> keys = new LinkedHashSet<>();
        final Map<BigInteger, PublicKey> expected = new LinkedHashMap<>();
        for (int power = 0; power < KEY_COUNT; power++) {
            final BigInteger amount = BigInteger.TWO.pow(power);
            final PrivateKey privateKey = PrivateKey.generateRandom();
            final PublicKey publicKey = PrivateKey.derivePublicKey(privateKey);

            final KeyEntity key = new KeyEntity();
            key.setId(UUID.randomUUID());
            key.setAmount(amount);
            key.setPublicKey(publicKey.toString());
            key.setKeySet(keySet);
            keys.add(key);
            expected.put(amount, publicKey);
        }
        return new ProvisionedKeySet(keySet, keys, expected);
    }

    private static KeySetEntity emptyKeySet() {
        final MintEntity mint = new MintEntity();
        mint.setId(UUID.randomUUID());

        final KeySetEntity keySet = new KeySetEntity();
        keySet.setId(UUID.randomUUID());
        keySet.setKeySetId("009a1f293253e41e");
        keySet.setUnit("sat");
        keySet.setMint(mint);
        return keySet;
    }
}
