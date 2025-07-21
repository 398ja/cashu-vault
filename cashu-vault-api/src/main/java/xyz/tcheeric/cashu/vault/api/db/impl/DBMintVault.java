package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Mint;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.crypto.util.KeySetDerivation;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.config.KeysetConfiguration;
import xyz.tcheeric.cashu.vault.api.config.MintConfiguration;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DBMintVault extends DBVault<MintConfiguration, MintEntity> {

    public DBMintVault(MintConfiguration configuration) {
        super(configuration, new CashuVaultApplication().vaultMintClient());
    }

    @Override
    public void store() {
        MintConfiguration mintConfiguration = getConfiguration();
        VaultClient<MintEntity> client = getClient();

        MintEntity mintEntity = new MintEntity();
        mintEntity.setId(UUID.fromString(mintConfiguration.getId()));
        client.store(mintEntity);
    }

    private Set<ProofEntity> getProofs(MintConfiguration mintConfiguration) {
        VaultClient<MintEntity> mintVaultClient = new VaultClient<>(MintEntity.class);
        return mintVaultClient.retrieve(mintConfiguration.getId()).getProofs();
    }

    private Set<KeySetEntity> getKeySets(MintConfiguration mintConfiguration) {
        VaultClient<MintEntity> mintVaultClient = new VaultClient<>(MintEntity.class);
        return mintVaultClient.retrieve(mintConfiguration.getId()).getKeySets();
    }

    @Override
    public String retrieve(boolean archived) throws CashuErrorException {
        MintEntity mintEntity = retrieveEntity();
        return mintEntity.isArchived() != archived ? null : mintEntity.getId().toString();
    }

    @Override
    protected MintEntity retrieveEntity() throws CashuErrorException {
        MintConfiguration mintConfiguration = getConfiguration();
        VaultClient<MintEntity> client = getClient();

        MintEntity mintEntity = client.retrieve(mintConfiguration.getId());
        if (mintEntity == null) {
            throw new CashuErrorException("Mint not found");
        }
        return mintEntity;
    }

    @Override
    public void archive() throws CashuErrorException {
        VaultClient<MintEntity> client = getClient();
        MintEntity mintEntity = retrieveEntity();
        client.archive(mintEntity.getId().toString());
    }

    @Override
    public void delete() throws CashuErrorException {
        VaultClient<MintEntity> client = getClient();
        MintEntity mintEntity = retrieveEntity();
        client.delete(mintEntity.getId().toString());
    }

    public static List<Mint> load(boolean archive) {
        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        List<MintEntity> mintEntities = vaultClient.retrieveAll();
        return mintEntities.stream()
                .map(mintEntity -> {
                    MintConfiguration mintConfiguration = new MintConfiguration(mintEntity.getId().toString());
                    try {
                        return load(mintConfiguration, archive, false);
                    } catch (CashuErrorException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    public static Mint load(UUID mintId, boolean archive) throws CashuErrorException {
        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        MintEntity mintEntity = vaultClient.retrieve(mintId.toString());
        if(mintEntity.isArchived() != archive) {
            throw new CashuErrorException("Mint with ID " + mintId + " not found or not archived");
        }

        MintConfiguration mintConfiguration = new MintConfiguration(mintEntity.getId().toString());
        return load(mintConfiguration, archive, false);
    }

    public static Mint load(String keySetId, boolean archive) throws CashuErrorException {
        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        List<MintEntity> mintEntities = vaultClient.retrieveAll();
        return mintEntities.stream()
                .map(mintEntity -> {
                    MintConfiguration mintConfiguration = new MintConfiguration(mintEntity.getId().toString());
                    try {
                        return load(mintConfiguration, archive, false);
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

    public static Mint load(@NonNull MintConfiguration mintConfiguration, boolean archive, boolean lazy) throws CashuErrorException {
        Mint mint = new Mint(mintConfiguration.getId());

        if (lazy) {
            return mint;
        }

        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        MintEntity mintEntity = vaultClient.retrieve(mintConfiguration.getId());
        if (mintEntity == null) {
            throw new CashuErrorException("Mint not found");
        }

        mintEntity.getKeySets().forEach(keySetEntity -> {
            KeySet keySet = null;
            try {
                String keySetId = getKeySetId(keySetEntity);
                keySet = DBKeySetVault.load(new KeysetConfiguration(mintConfiguration, keySetId, keySetEntity.getUnit()), archive);
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
            mint.addKeySet(keySet);
        });

        return mint;
    }

    public static Mint load(@NonNull MintConfiguration mintConfiguration, String keySetId, boolean archive, boolean lazy) throws CashuErrorException {
        Mint mint = new Mint(mintConfiguration.getId());

        if (lazy) {
            return mint;
        }

        VaultClient<MintEntity> vaultClient = new VaultClient<>(MintEntity.class);
        MintEntity mintEntity = vaultClient.retrieve(mintConfiguration.getId());
        if (mintEntity == null) {
            throw new CashuErrorException("Mint not found");
        }

        mintEntity.getKeySets().forEach(keySetEntity -> {
            try {
                if (getKeySetId(keySetEntity).equals(keySetId)) {
                    KeySet keySet = DBKeySetVault.load(new KeysetConfiguration(mintConfiguration, keySetId, keySetEntity.getUnit()), archive);
                    mint.addKeySet(keySet);
                }
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
        });

        return mint;
    }

    public String getUnit(@NonNull String keySetId) throws CashuErrorException {
        MintEntity mintEntity = retrieveEntity();

        return mintEntity.getKeySets().stream()
                .filter(keySetEntity -> getKeySetId(keySetEntity).equals(keySetId))
                .map(KeySetEntity::getUnit)
                .findFirst()
                .orElseThrow(() -> new CashuErrorException("KeySet with ID " + keySetId + " not found"));
    }

    public String getPrivateKey(@NonNull String unit, Integer amount) throws CashuErrorException {
        MintEntity mintEntity = retrieveEntity();

        return mintEntity.getKeySets().stream()
                .filter(keySetEntity -> keySetEntity.getUnit().equals(unit))
                .flatMap(keySetEntity -> keySetEntity.getKeys().stream())
                .filter(keyEntity -> keyEntity.getAmount().equals(amount))
                .map(keyEntity -> PublicKey.fromString(keyEntity.getPrivateKey()).toString())
                .findFirst()
                .orElseThrow(() -> new CashuErrorException("Private key for unit " + unit + " and amount " + amount + " not found"));
    }

    private static String getKeySetId(KeySetEntity keySetEntity) {
        Map<BigInteger, byte[]> keys = getKeys(keySetEntity);
        return KeySetDerivation.getId(keys);
    }

    private static Map<BigInteger, byte[]> getKeys(KeySetEntity keySetEntity) {
        Map<BigInteger, byte[]> keys = new HashMap<>();
        keySetEntity.getKeys().forEach(keyEntity -> {
            keys.put(keyEntity.getAmount(), PublicKey.fromString(keyEntity.getPrivateKey()).getBytes());
        });
        return keys;
    }
}
