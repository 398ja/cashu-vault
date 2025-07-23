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
    public ProofEntity retrieveEntity() throws CashuErrorException {
        ProofEntity entity = getEntity();
        ProofClient client = new ProofClient();

        ProofEntity proofEntity = client.getByMintIdAndSecret(entity.getMint().getId().toString(), entity.getSecret());
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

    @Override
    public String retrieve(boolean archived) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity();
        return proofEntity.isArchived() != archived ? null : proofEntity.getId().toString();
    }


    public String retrieveSignature(@NonNull String secret, boolean archived) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity();
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

    public String retrieveWitness(@NonNull String secret) throws CashuErrorException {
        ProofEntity proofEntity = retrieveEntity();
        if (!secret.equals(proofEntity.getSecret())) {
            throw new CashuErrorException("Secret does not match for the proof");
        }

        return proofEntity.getWitness();
    }

    @Override
    public void archive() throws CashuErrorException {
        PROOF_STATE_LOCK.lock();

        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieveEntity();
            proofEntity.setArchived(true);
            proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }

    @Override
    public void delete() throws CashuErrorException {
        ProofClient client = new ProofClient();

        ProofEntity proofEntity = retrieveEntity();
        client.delete(proofEntity.getId().toString());
    }

    public void invalidate() throws CashuErrorException {
        PROOF_STATE_LOCK.lock();
        try {
            ProofClient proofClient = new ProofClient();
            ProofEntity proofEntity = retrieveEntity();
            proofEntity.setState(ProofEntity.STATE_SPENT);
            proofClient.store(proofEntity);
        } finally {
            PROOF_STATE_LOCK.unlock();
        }
    }
}
