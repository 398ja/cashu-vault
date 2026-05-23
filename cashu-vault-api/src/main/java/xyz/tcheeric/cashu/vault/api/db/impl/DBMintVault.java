package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Mint;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.crypto.util.KeySetDerivation;
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

    public static Mint load(UUID mintId, boolean archive) throws CashuErrorException {
        VaultClient<MintEntity> vaultClient = VaultClientFactory.getClient(MintEntity.class);
        MintEntity mintEntity = vaultClient.retrieve(mintId.toString());
        if (mintEntity.isArchived() != archive) {
            throw new CashuErrorException("Mint with ID " + mintId + " not found or not archived");
        }

        return load(mintEntity, archive, false);
    }

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

        VaultClient<MintEntity> vaultClient = VaultClientFactory.getClient(MintEntity.class);
        MintEntity loaded = vaultClient.retrieve(mintEntity.getId().toString());
        if (loaded == null) {
            throw new CashuErrorException("Mint not found");
        }

        loaded.getKeySets().forEach(keySetEntity -> {
            KeySet keySet;
            try {
                keySet = DBKeySetVault.load(keySetEntity, archive);
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
            mint.addKeySet(keySet);
        });

        return mint;
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
