package xyz.tcheeric.cashu.vault.api.db.impl;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.DBVault;
import xyz.tcheeric.cashu.vault.api.config.ProofConfiguration;
import xyz.tcheeric.cashu.vault.db.CashuVaultApplication;
import xyz.tcheeric.cashu.vault.db.client.ProofClient;
import xyz.tcheeric.cashu.vault.db.client.VaultClient;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import java.util.concurrent.locks.ReentrantLock;

@Log
public class DBProofVault extends DBVault<ProofConfiguration, ProofEntity> {

    private static final ReentrantLock PROOF_STATE_LOCK = new ReentrantLock();

    public DBProofVault(ProofConfiguration configuration) {
        super(configuration, new CashuVaultApplication().vaultProofClient());
    }

    @Override
    public void store() {
        ProofConfiguration proofConfiguration = getConfiguration();
        VaultClient<ProofEntity> client = getClient();

        ProofEntity proofEntity = new ProofEntity();
        proofEntity.setMint(getMint(proofConfiguration));
        proofEntity.setWitness(proofConfiguration.getWitness());
        proofEntity.setSecret(proofConfiguration.getHashToCurveSecret());
        proofEntity.setUnblindedSignature(proofConfiguration.getUnblindedSignature());

        client.store(proofEntity);
    }

    @Override
    public ProofEntity retrieveEntity() throws CashuErrorException {
        ProofConfiguration proofConfiguration = getConfiguration();
        ProofClient client = new ProofClient();

        ProofEntity proofEntity = client.getByMintIdAndSecret(proofConfiguration.getMint().getId(), proofConfiguration.getHashToCurveSecret());
        if (proofEntity == null) {
            throw new CashuErrorException("Proof not found");
        }
        return proofEntity;
    }

    public void storePending() {
        ProofConfiguration proofConfiguration = getConfiguration();
        VaultClient<ProofEntity> client = getClient();

        ProofEntity proofEntity = new ProofEntity();
        proofEntity.setMint(getMint(proofConfiguration));
        proofEntity.setWitness(proofConfiguration.getWitness());
        proofEntity.setSecret(proofConfiguration.getHashToCurveSecret());
        proofEntity.setUnblindedSignature(proofConfiguration.getUnblindedSignature());
        proofEntity.setState(ProofEntity.STATE_PENDING);

        client.store(proofEntity);
    }

    private MintEntity getMint(ProofConfiguration proofConfiguration) {
        VaultClient<MintEntity> mintVaultClient = new VaultClient<>(MintEntity.class);
        return mintVaultClient.retrieve(proofConfiguration.getMint().getId());
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
