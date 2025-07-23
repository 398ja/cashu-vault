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

    public DBProofVault(ProofEntity entity) {
        super(entity, new CashuVaultApplication().vaultProofClient());
    }

    @Override
    public void store() {
        ProofEntity proofEntity = getEntity();
        VaultClient<ProofEntity> client = getClient();

        proofEntity.setMint(getMint(proofEntity));
        client.store(proofEntity);
    }

    @Override
    public ProofEntity retrieveEntity(@NonNull String id) throws CashuErrorException {
        ProofClient client = new ProofClient();
        ProofEntity proofEntity = client.retrieve(id);
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found");
        }
        return proofEntity;
    }

    public void storePending() {
        ProofEntity proofEntity = getEntity();
        VaultClient<ProofEntity> client = getClient();

        proofEntity.setMint(getMint(proofEntity));
        proofEntity.setState(ProofEntity.STATE_PENDING);

        client.store(proofEntity);
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
    public void archive() throws CashuErrorException {
        PROOF_STATE_LOCK.lock();

        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieveEntity(getEntity().getId().toString());
            proofEntity.setArchived(true);
            proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }

    @Override
    public void delete() {
        ProofClient client = new ProofClient();
        client.delete(getEntity().getId().toString());
    }

    public void invalidate() throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieveEntity(getEntity().getId().toString());
            proofEntity.setState(ProofEntity.STATE_SPENT);
            proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }
}
