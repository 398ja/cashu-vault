package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.List;

import static xyz.tcheeric.cashu.vault.api.VaultClientFactory.getClient;

@Log
public final class DBProofVault extends DBVault<ProofEntity> {

    public DBProofVault() {
        this(VaultClientFactory.getClient(ProofEntity.class));
    }

    public DBProofVault(VaultClient<ProofEntity> client) {
        super(client);
    }

    @Override
    public ProofEntity store(ProofEntity proofEntity) throws CashuErrorException {
        proofEntity.setMint(getMint(proofEntity));
        return client.store(proofEntity);
    }

    @Override
    protected ProofEntity retrieveEntity(@NonNull String id) throws CashuErrorException {
        ProofEntity proofEntity = client.retrieve(id);
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found");
        }
        return proofEntity;
    }

/*
    public static DBProofVault retrieveProof(@NonNull String id) throws CashuErrorException {
        DBProofVault proofVault = new DBProofVault(null);
        return new DBProofVault(proofVault.retrieveEntity(id));
    }
*/

    public static ProofEntity retrieveProof(@NonNull String secret) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return retrieveProof(secret, client);
    }

    public static ProofEntity retrieveProof(@NonNull String secret, ProofClient client) throws CashuErrorException {
        ProofEntity proofEntity = client.getBySecret(secret);
        return proofEntity;
    }

    public static ProofEntity retrieveProof(@NonNull String mintId, @NonNull String secret) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return retrieveProof(mintId, secret, client);
    }

    public static ProofEntity retrieveProof(@NonNull String mintId, @NonNull String secret, ProofClient client) throws CashuErrorException {
        ProofEntity proofEntity = client.getByMintIdAndSecret(mintId, secret);
        return proofEntity;
    }

    public static ProofEntity retrieveProof(String mintId, Integer amount) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return retrieveProof(mintId, amount, client);
    }

    public static ProofEntity retrieveProof(String mintId, Integer amount, ProofClient client) throws CashuErrorException {
        ProofEntity proofEntity = client.getByMintAndAmount(mintId, amount);
        return proofEntity;
    }

    public static ProofEntity retrieveProofByUnblindedSignature(@NonNull String mintId,
            @NonNull String unblindedSignature) throws CashuErrorException {
        ProofClient client = new ProofClient();
        ProofEntity proofEntity = client.getByMintAndUnblindedSignature(mintId, unblindedSignature);
        return proofEntity;
    }

    public ProofEntity storePending(@NonNull ProofEntity proofEntity) throws CashuErrorException {
        VaultClient<ProofEntity> client = getClient(ProofEntity.class);

        proofEntity.setMint(getMint(proofEntity));
        proofEntity.setState(ProofEntity.STATE_PENDING);

        return client.store(proofEntity);
    }

    private MintEntity getMint(ProofEntity proofEntity) {
        VaultClient<MintEntity> mintVaultClient = getClient(MintEntity.class);
        return mintVaultClient.retrieve(proofEntity.getMint().getId().toString());
    }


/*
    public String retrieveSignature(@NonNull String secret, boolean archived) throws CashuErrorException {
        VaultClient<ProofEntity> client = getClient();
        client.re
        if (!secret.equals(proofEntity.getSecret())) {
            throw new CashuErrorException("Secret does not match for the proof");
        }

        return proofEntity.isArchived() != archived ? null : proofEntity.getUnblindedSignature();
    }

    public String retrievePending(@NonNull String secret) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity();
        if (!secret.equals(proofEntity.getSecret())) {
            throw new CashuErrorException("Secret does not match for the proof");
        }

        return proofEntity.getUnblindedSignature();
    }
*/

/*
    public String retrieveWitness(@NonNull String secret) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity();
        if (!secret.equals(proofEntity.getSecret())) {
            throw new CashuErrorException("Secret does not match for the proof");
        }

        return proofEntity.getWitness();
    }
*/

    /**
     * Marks the proof archived through the vault's archive endpoint.
     *
     * <p>This used to load the row, set the flag and re-POST the whole entity to the store
     * endpoint, which only worked while store would overwrite an existing row. Store is
     * insert-only now (cashu-vault#154).
     */
    @Override
    public ProofEntity archive(String id) throws CashuErrorException {
        return VaultClientFactory.proofClient().archive(id);
    }

    /**
     * Always refuses. The vault is the mint's only record of spent proofs, so it offers no way
     * to delete one: removing a SPENT row would make that proof spendable again (cashu-vault#154).
     */
    @Override
    public void delete(String id) throws CashuErrorException {
        throw new CashuErrorException("Proofs cannot be deleted: the vault is the record of spent proofs");
    }

    /**
     * Marks one stored proof spent, by id.
     *
     * <p>Resolves the row to its mint and secret, then goes through {@link #markSpent}. It no
     * longer writes the row back with its state changed, which relied on store overwriting an
     * existing row (cashu-vault#154).
     *
     * @throws CashuErrorException if the proof does not exist or is not SPENT afterwards
     */
    public ProofEntity invalidate(String id) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity(id);
        String mintId = proofEntity.getMint().getId().toString();
        if (markSpent(mintId, List.of(proofEntity.getSecret())) != 1) {
            throw new CashuErrorException("Proof could not be marked spent");
        }
        proofEntity.setState(ProofEntity.STATE_SPENT);
        proofEntity.setHoldId(null);
        proofEntity.setHoldKind(null);
        return proofEntity;
    }

    /**
     * cashu-vault#154 — records the named proofs of a mint as spent, from UNSPENT or PENDING.
     *
     * @return how many of {@code secrets} are SPENT after the call
     */
    public static int markSpent(String mintId, List<String> secrets) {
        return VaultClientFactory.proofClient().markSpent(mintId, secrets);
    }

    // ---------------------------------------------------------------
    // cashu-mint spec 002 T011 — melt-saga binding pass-through
    // ---------------------------------------------------------------

    /**
     * Spec 005 — atomic insert-or-claim pass-through. Submits already
     * Y-normalised {@link ProofEntity} rows; returns the total proof
     * count actually bound to {@code holdId}.
     */
    public static int insertOrClaimForHold(String mintId,
                                           String holdId,
                                           java.util.List<ProofEntity> proofs) {
        return VaultClientFactory.proofClient().insertOrClaimForHold(mintId, holdId, proofs);
    }

    /**
     * cashu-mint spec 002 T011 — atomically marks proofs PENDING and
     * binds them to the named melt saga via the vault REST API.
     *
     * @return number of rows actually transitioned UNSPENT → PENDING
     */
    public static int markPendingForHold(String mintId,
                                         String holdId,
                                         java.util.List<String> proofSecrets) {
        return VaultClientFactory.proofClient().markPending(mintId, holdId, proofSecrets);
    }

    /**
     * cashu-mint spec 002 T011 — commits a saga's PENDING proofs as
     * SPENT and clears the {@code hold_id} binding.
     */
    public static int commitSpentForHold(String holdId) {
        return VaultClientFactory.proofClient().commitSpent(holdId);
    }

    /**
     * cashu-mint spec 002 T011 — refunds a saga's PENDING proofs back
     * to UNSPENT and clears the {@code hold_id} binding.
     */
    public static int refundForHold(String holdId) {
        return VaultClientFactory.proofClient().refund(holdId);
    }
}
