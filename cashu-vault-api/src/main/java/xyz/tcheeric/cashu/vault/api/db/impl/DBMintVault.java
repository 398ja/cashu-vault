package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Mint;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.crypto.util.KeySetDerivation;
import org.springframework.web.client.HttpClientErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class DBMintVault extends DBVault<MintEntity> {

    public DBMintVault() {
        this(VaultClientFactory.getClient(MintEntity.class));
    }

    public DBMintVault(VaultClient<MintEntity> client) {
        super(client);
    }

    @Override
    public MintEntity store(MintEntity mintEntity) throws CashuErrorException {
        return client.store(mintEntity);
    }

    private Set<ProofEntity> getProofs(MintEntity mintEntity) {
        VaultClient<MintEntity> mintVaultClient = VaultClientFactory.getClient(MintEntity.class);
        return mintVaultClient.retrieve(mintEntity.getId().toString()).getProofs();
    }

    private Set<KeySetEntity> getKeySets(MintEntity mintEntity) {
        VaultClient<MintEntity> mintVaultClient = VaultClientFactory.getClient(MintEntity.class);
        return mintVaultClient.retrieve(mintEntity.getId().toString()).getKeySets();
    }


    @Override
    protected MintEntity retrieveEntity(@NonNull String id) throws CashuErrorException {
        MintEntity mintEntity = client.retrieve(id);
        if (mintEntity == null) {
            throw new CashuErrorException("Mint not found");
        }
        return mintEntity;
    }

    /**
     * Every mint, each populated with one generation of its keysets: the active
     * keysets when {@code archive} is false, the retired ones when it is true.
     *
     * <p>{@code archive} selects a keyset generation here, not a set of mints. The two
     * archived flags are independent: a keyset retires while its mint goes on operating,
     * which is ordinary NUT-02 rotation rather than an edge case. The V5 migration exists
     * to permit exactly that, replacing UNIQUE (unit, mint_id) with an index unique only
     * where {@code archived = false} so retired keysets may accumulate under a live mint.
     * On the one real deployment measured, all three archived keysets hung off mints that
     * were themselves active.
     *
     * <p>So this must not filter mints on {@code mintEntity.isArchived()}. That filter runs
     * before the keyset loop in {@link #load(MintEntity, boolean, boolean)} and, with no
     * archived mints, makes {@code load(true)} empty and every archived keyset unreachable
     * through this API. That is the only path to them, and NUT-02 requires retired keysets
     * to go on redeeming, so it would strand the funds of any wallet holding their proofs.
     *
     * <p>A mint therefore appears in both generations, carrying different keysets in each.
     * A caller that concatenates the two and wants distinct mints must deduplicate by id.
     */
    public static List<Mint> load(boolean archive) {
        VaultClient<MintEntity> vaultClient = VaultClientFactory.getClient(MintEntity.class);
        List<MintEntity> mintEntities = vaultClient.retrieveAll();
        return mintEntities.stream()
                .map(mintEntity -> {
                    try {
                        return load(mintEntity, archive, false);
                    } catch (CashuErrorException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    /**
     * One mint, populated with the keyset generation {@code archive} selects.
     *
     * <p>Unlike the list overload, this one also tests the MINT's own archived flag and
     * throws when it differs. That conflates the two independent flags: for an active mint
     * holding retired keysets, the ordinary result of NUT-02 rotation, {@code load(id, true)}
     * throws rather than answering the mint with its archived keysets. MeltTokensTask in
     * cashu-mint calls exactly that, so redeeming a proof from a rotated keyset fails on a
     * mint that is up and serving.
     *
     * <p>Left as-is deliberately: changing it alters an error contract callers dispatch on,
     * which is a separate change from the list overload this commit fixes, and the two
     * should not ride together. Tracked rather than silently widened.
     */
    public static Mint load(UUID mintId, boolean archive) throws CashuErrorException {
        VaultClient<MintEntity> vaultClient = VaultClientFactory.getClient(MintEntity.class);
        MintEntity mintEntity = vaultClient.retrieve(mintId.toString());
        if (mintEntity.isArchived() != archive) {
            throw new CashuErrorException("Mint with ID " + mintId + " not found or not archived");
        }

        return load(mintEntity, archive, false);
    }

    /**
     * The mint that owns {@code keySetId} within the generation {@code archive} selects.
     *
     * <p>Free of the conflation the other two overloads had: it never tests the mint's own
     * archived flag, so an archived keyset is still found on an active mint. Keeping it that
     * way is what lets a rotated keyset be resolved back to its live mint.
     */
    public static Mint load(String keySetId, boolean archive) throws CashuErrorException {
        VaultClient<MintEntity> vaultClient = VaultClientFactory.getClient(MintEntity.class);
        List<MintEntity> mintEntities = vaultClient.retrieveAll();
        return mintEntities.stream()
                .map(mintEntity -> {
                    try {
                        return load(mintEntity, archive, false);
                    } catch (CashuErrorException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(mint -> mint.getKeySets()
                        .stream()
                        .anyMatch(keySet -> keySet.getId().equals(keySetId)))
                .findFirst()
                .orElseThrow(() -> new CashuErrorException("Mint with keySetId " + keySetId + " not found"));

    }

    public static Mint load(@NonNull MintEntity mintEntity, boolean archive, boolean lazy) throws CashuErrorException {
        Mint mint = new Mint(mintEntity.getId().toString());

        if (lazy) {
            return mint;
        }

        // Fetched through the keyset endpoint rather than read off
        // MintEntity.getKeySets(): that relation is @JsonIgnore, so a mint retrieved
        // over REST always reports no keysets and this returned an empty mint.
        for (KeySetEntity keySetEntity : keySetsOf(mintEntity.getId().toString())) {
            // archive selects which generation is wanted: the active keyset, or the
            // retired ones that must go on redeeming (NUT-02).
            if (keySetEntity.isArchived() != archive) {
                continue;
            }
            mint.addKeySet(DBKeySetVault.load(keySetEntity, archive));
        }

        return mint;
    }

    /**
     * A mint's keysets, with "this mint has none" answered as an empty set.
     *
     * <p>The client signals that by throwing rather than by answering empty: the
     * vault answers 404, and a 200 carrying an empty body raises
     * IllegalArgumentException. A mint nobody has provisioned yet holds none, which
     * is an answer rather than a failure.
     */
    private static Set<KeySetEntity> keySetsOf(String mintId) {
        try {
            return VaultClientFactory.keySetClient().getByMintId(mintId);
        } catch (HttpClientErrorException.NotFound | IllegalArgumentException e) {
            return Set.of();
        }
    }

    public static Mint load(@NonNull MintEntity mintEntity, String keySetId, boolean archive, boolean lazy) throws CashuErrorException {
        Mint mint = new Mint(mintEntity.getId().toString());

        if (lazy) {
            return mint;
        }

        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        MintEntity loaded = vaultClient.retrieve(mintEntity.getId().toString());
        if (loaded == null) {
            throw new CashuErrorException("Mint not found");
        }

        loaded.getKeySets().forEach(keySetEntity -> {
            try {
                if (getKeySetId(keySetEntity).equals(keySetId)) {
                    KeySet keySet = DBKeySetVault.load(keySetEntity, archive);
                    mint.addKeySet(keySet);
                }
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
        });

        return mint;
    }

    public String getUnit(MintEntity mintEntity, @NonNull String keySetId) throws CashuErrorException {
        return mintEntity.getKeySets().stream()
                .filter(keySetEntity -> getKeySetId(keySetEntity).equals(keySetId))
                .map(KeySetEntity::getUnit)
                .findFirst()
                .orElseThrow(() -> new CashuErrorException("KeySet with ID " + keySetId + " not found"));
    }

    public String getPrivateKey(MintEntity mintEntity, @NonNull String unit, Integer amount) throws CashuErrorException {
        return mintEntity.getKeySets().stream()
                .filter(keySetEntity -> keySetEntity.getUnit().equals(unit))
                .flatMap(keySetEntity -> keySetEntity.getKeys().stream())
                .filter(keyEntity -> amount != null && keyEntity.getAmount().equals(BigInteger.valueOf(amount.longValue())))
                .map(keyEntity -> PublicKey.fromString(keyEntity.getPrivateKey()).toString())
                .findFirst()
                .orElseThrow(() -> new CashuErrorException("Private key for unit " + unit + " and amount " + amount + " not found"));
    }

    private static String getKeySetId(KeySetEntity keySetEntity) {
        Map<BigInteger, byte[]> keys = getKeys(keySetEntity);
        return KeySetDerivation.getId(keys);
    }

    private static Map<BigInteger, byte[]> getKeys(KeySetEntity keySetEntity) {
        // Use TreeMap instead of HashMap to prevent hash collision DoS attacks
        Map<BigInteger, byte[]> keys = new TreeMap<>();
        keySetEntity.getKeys().forEach(keyEntity -> {
            keys.put(keyEntity.getAmount(), PublicKey.fromString(keyEntity.getPrivateKey()).getBytes());
        });
        return keys;
    }
}
