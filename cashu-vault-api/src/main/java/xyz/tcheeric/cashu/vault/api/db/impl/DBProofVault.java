package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.concurrent.locks.ReentrantLock;

@Log
public class DBProofVault extends DBVault<ProofEntity> {

    private static final ReentrantLock PROOF_STATE_LOCK = new ReentrantLock();

    public DBProofVault() {
        super(new CashuVaultApplication().vaultProofClient());
    }

    @Override
    public ProofEntity store(ProofEntity proofEntity) {
        VaultClient<ProofEntity> client = getClient();
        proofEntity.setMint(getMint(proofEntity));
        return client.store(proofEntity);
    }

    @Override
    public ProofEntity retrieve(@NonNull String id) throws CashuErrorException {
        ProofClient client = new ProofClient();
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
        ProofClient client = new ProofClient();
        ProofEntity proofEntity = client.getBySecret(secret);
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found for secret: " + secret);
        }
        return proofEntity;
    }

    public static ProofEntity retrieveProof(@NonNull String mintId, @NonNull String secret) throws CashuErrorException {
        ProofClient client = new ProofClient();
        ProofEntity proofEntity = client.getByMintIdAndSecret(mintId, secret);
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found for mintId: " + mintId + " and secret: " + secret);
        }
        return proofEntity;
    }

    public static ProofEntity retrieveProof(String mintId, Integer amount) throws CashuErrorException {
        ProofClient client = new ProofClient();
        ProofEntity proofEntity = client.getByMintAndAmount(mintId, amount);
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found for mintId: " + mintId + " and amount: " + amount);
        }
        return proofEntity;
    }

    public ProofEntity storePending(ProofEntity proofEntity) {
        VaultClient<ProofEntity> client = getClient();
        proofEntity.setMint(getMint(proofEntity));
        proofEntity.setState(ProofEntity.STATE_PENDING);
        return client.store(proofEntity);
    }

    private MintEntity getMint(ProofEntity proofEntity) {
        VaultClient<MintEntity> mintVaultClient = new VaultClient<>(MintEntity.class);
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

    @Override
    public ProofEntity archive(String id) throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieve(id);
            proofEntity.setArchived(true);
            return proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }

    @Override
    public void delete(String id) {
        ProofClient client = new ProofClient();
        client.delete(id);
    }

    public ProofEntity invalidate(String id) throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieve(id);
            proofEntity.setState(ProofEntity.STATE_SPENT);
            return proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }
}
