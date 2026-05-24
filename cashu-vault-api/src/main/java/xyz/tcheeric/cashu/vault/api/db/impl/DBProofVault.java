package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.VaultClientFactory;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.dto.TombstoneResponse;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.concurrent.locks.ReentrantLock;

import static xyz.tcheeric.cashu.vault.api.VaultClientFactory.getClient;

@Log
public final class DBProofVault extends DBVault<ProofEntity> {

    private static final ReentrantLock PROOF_STATE_LOCK = new ReentrantLock();

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

    /**
     * Spec 001 / FR-005 — global secret lookup REMOVED. Use {@link #retrieveProof(String, String)} instead.
     */
    public static ProofEntity retrieveProof(@NonNull String mintId, @NonNull String secret) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return retrieveProof(mintId, secret, client);
    }

    public static ProofEntity retrieveProof(@NonNull String mintId, @NonNull String secret, ProofClient client) throws CashuErrorException {
        return client.getByMintIdAndSecret(mintId, secret);
    }

    public static ProofEntity retrieveProof(String mintId, Integer amount) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return retrieveProof(mintId, amount, client);
    }

    public static ProofEntity retrieveProof(String mintId, Integer amount, ProofClient client) throws CashuErrorException {
        return client.getByMintAndAmount(mintId, amount);
    }

    public static ProofEntity retrieveProofByUnblindedSignature(@NonNull String mintId,
                                                                @NonNull String unblindedSignature) throws CashuErrorException {
        ProofClient client = new ProofClient();
        return client.getByMintAndUnblindedSignature(mintId, unblindedSignature);
    }

    public ProofEntity storePending(@NonNull ProofEntity proofEntity) throws CashuErrorException {
        VaultClient<ProofEntity> client = getClient(ProofEntity.class);
        proofEntity.setMint(getMint(proofEntity));
        // FR-008 — never re-save with mutated state; insert via store then transition via /state.
        ProofEntity stored = client.store(proofEntity);
        return transitionState(stored, ProofEntity.STATE_PENDING);
    }

    private MintEntity getMint(ProofEntity proofEntity) {
        VaultClient<MintEntity> mintVaultClient = getClient(MintEntity.class);
        return mintVaultClient.retrieve(proofEntity.getMint().getId().toString());
    }

    @Override
    public ProofEntity archive(String id) throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofClient proofClient = VaultClientFactory.proofClient();
            ProofEntity proofEntity = retrieveEntity(id);
            proofEntity.setArchived(true);
            proofClient.store(proofEntity);
            return proofEntity;
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }

    /**
     * Spec 001 / FR-001 — physical deletion is forbidden. Use {@link #tombstone(String, String, String, boolean)} instead.
     */
    @Override
    public void delete(String id) throws CashuErrorException {
        throw new UnsupportedOperationException(
                "Use DBProofVault.tombstone(mintId, secret, reason, force) — physical deletion of proofs is forbidden");
    }

    /**
     * Spec 001 / FR-002, FR-003 — admin tombstone.
     */
    public TombstoneResponse tombstone(@NonNull String mintId, @NonNull String secret,
                                       @NonNull String reason, boolean force) throws CashuErrorException {
        ProofClient client = VaultClientFactory.proofClient();
        return client.tombstone(mintId, secret, reason, force);
    }

    /**
     * Spec 001 / FR-008 — state-only transition through the dedicated /state endpoint.
     */
    public ProofEntity invalidate(String id) throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofEntity proofEntity = retrieveEntity(id);
            return transitionState(proofEntity, ProofEntity.STATE_SPENT);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }

    private static ProofEntity transitionState(ProofEntity proof, String toState) throws CashuErrorException {
        ProofClient proofClient = VaultClientFactory.proofClient();
        String mintId = proof.getMint() != null && proof.getMint().getId() != null
                ? proof.getMint().getId().toString()
                : null;
        if (mintId == null) {
            throw new CashuErrorException("proof has no mint");
        }
        return proofClient.transitionState(mintId, proof.getSecret(), toState);
    }
}
