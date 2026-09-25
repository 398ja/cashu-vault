package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.KeyPublicKeys;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.KeySetVaultClient;
import xyz.tcheeric.cashu.vault.db.client.KeyVaultClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;
import xyz.tcheeric.cashu.vault.db.model.KeySetEntity;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;

import java.util.Set;

public final class DBKeySetVault extends DBVault<KeySetEntity> {

    public DBKeySetVault() {
        this(VaultClientFactory.getClient(KeySetEntity.class));
    }

    public DBKeySetVault(VaultClient<KeySetEntity> client) {
        super(client);
    }

    @Override
    public KeySetEntity store(KeySetEntity keySetEntity) throws CashuErrorException {
        keySetEntity.setMint(getMint(keySetEntity));
        return client.store(keySetEntity);
    }

    private MintEntity getMint(KeySetEntity keySetEntity) {
        VaultClient<MintEntity> mintEntityVaultClient = VaultClientFactory.getClient(MintEntity.class);
        return mintEntityVaultClient.retrieve(keySetEntity.getMint().getId().toString());
    }

    private Set<KeyEntity> getKeys(KeySetEntity keySetEntity) {
        KeyVaultClient keyVaultClient = VaultClientFactory.keyClient();
        return keyVaultClient.getKeysByUnit(keySetEntity.getUnit());
    }

    @Override
    protected KeySetEntity retrieveEntity(String id) throws CashuErrorException {
        KeySetEntity keySetEntity = client.retrieve(id);
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found");
        }
        return keySetEntity;
    }

    public KeySetEntity retrieveByMintIdAndUnit(@NonNull String mintId, @NonNull String unit) throws CashuErrorException {
        KeySetVaultClient ksClient = VaultClientFactory.keySetClient();
        KeySetEntity keySetEntity = ksClient.getByMintId(mintId).stream()
                .filter(k -> k.getUnit().equals(unit))
                .findFirst()
                .orElse(null);
        if (keySetEntity == null) {
            throw new CashuErrorException("Keyset not found for mintId: " + mintId + " and unit: " + unit);
        }
        return keySetEntity;
    }

    /**
     * Loads a keyset and the public keys it advertises.
     *
     * <p>The keys are fetched by keyset id rather than read off
     * {@code keySetEntity.getKeys()}: that relation is {@code @JsonIgnore}, so a
     * client that reached this entity over REST always sees it empty and would
     * build a keyset advertising no denominations at all.
     *
     * <p>What is published is the *public* key derived from the signing key, and it now comes
     * back on the batch response itself, so this costs one call per keyset rather than one per
     * key (issue #146). It used to read every private key through
     * {@link VaultClientFactory#keyVault()} purely to derive the public key and discard the
     * private one: 424 key GETs for 58 distinct key ids over six measured swaps on staging,
     * each triggering a HashiCorp read.
     *
     * <p>The NUT-02 {@code input_fee_ppk} is carried through as stored. It is what the
     * mint charges on a swap and what it publishes on {@code /v1/keysets}, so dropping
     * it here would price every transaction at zero however the keyset was configured.
     */
    public static KeySet load(@NonNull KeySetEntity keySetEntity, boolean archive) throws CashuErrorException {
        Keys keys = new Keys();
        for (KeyEntity key : VaultClientFactory.keyClient()
                .getKeysByKeySetId(keySetEntity.getId().toString())) {
            keys.put(key.getAmount(), publicKeyOf(key, keySetEntity));
        }
        return KeySet.builder()
                .id(keySetEntity.getKeySetId())
                .unit(keySetEntity.getUnit())
                .keys(keys)
                .partPerThousand(keySetEntity.getInputFeePpk())
                .build();
    }

    /**
     * The public key a key advertises, without a round trip when the batch response carried one.
     *
     * <p>The fallback exists for rows written before the public_key column did. Those are
     * backfilled once at vault startup, so this path is transitional and costs what the whole
     * loop used to cost; it is kept because a keyset that silently dropped a denomination would
     * publish a mint unable to sign for it.
     */
    private static PublicKey publicKeyOf(KeyEntity key, KeySetEntity keySetEntity)
            throws CashuErrorException {
        if (key.getPublicKey() != null) {
            return PublicKey.fromString(key.getPublicKey());
        }
        return deriveFromStoredPrivateKey(key, keySetEntity);
    }

    private static PublicKey deriveFromStoredPrivateKey(KeyEntity key, KeySetEntity keySetEntity)
            throws CashuErrorException {
        KeyEntity resolved = VaultClientFactory.keyVault().retrieve(key.getId().toString());
        String privateKeyHex = resolved != null ? resolved.getPrivateKey() : null;
        if (privateKeyHex == null) {
            throw new CashuErrorException(
                    "No key material for amount " + key.getAmount() + " of keyset "
                            + keySetEntity.getKeySetId());
        }
        return PublicKey.fromString(KeyPublicKeys.deriveFrom(privateKeyHex));
    }
}
