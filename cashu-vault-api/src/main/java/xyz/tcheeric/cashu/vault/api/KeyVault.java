package xyz.tcheeric.cashu.vault.api;

import jakarta.annotation.Nonnull;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.model.KeyEntity;

import java.math.BigInteger;

/**
 * Polymorphic accessor for amount-keyed key lookups across the active backend.
 *
 * <p>Both {@link xyz.tcheeric.cashu.vault.api.db.impl.DBKeyVault DBKeyVault}
 * (DB-only) and {@link xyz.tcheeric.cashu.vault.api.Vault HCKeyVault} (HashiCorp
 * Vault enriched) implement this interface so callers can route through
 * {@link VaultClientFactory#keyVault()} and pick up the active backend at
 * runtime — most importantly so HCKeyVault's
 * {@code enrichWithVaultSecret(...)} fires and {@code KeyEntity.privateKey}
 * is populated before the caller dereferences it.
 *
 * <p>Without this typed accessor, callers that directly instantiate
 * {@code DBKeyVault} (e.g. {@code MintProtocolUtil.getPrivateKey}) bypass the
 * factory and never trigger the Hashi enrichment path, producing a null
 * {@code privateKey} and the silent mint-signing failures observed on
 * 2026-05-24.
 */
public interface KeyVault extends Vault<KeyEntity> {

    KeyEntity retrieveByAmount(@Nonnull BigInteger amount, @Nonnull String keySetId) throws CashuErrorException;
}
