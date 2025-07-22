package xyz.tcheeric.cashu.vault.api.db.impl;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.api.config.MintConfiguration;
import xyz.tcheeric.cashu.vault.api.config.ProofConfiguration;
import xyz.tcheeric.cashu.vault.db.model.MintEntity;
import xyz.tcheeric.cashu.vault.db.model.ProofEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DBProofVaultUnitTest {

    private static class TestProofVault extends DBProofVault {
        private final ProofEntity entity;
        TestProofVault(ProofConfiguration cfg, ProofEntity entity) {
            super(cfg);
            this.entity = entity;
        }
        @Override
        public ProofEntity retrieveEntity() { return entity; }
    }

    @Test
    void retrieveSignatureReturnsValue() throws Exception {
        ProofEntity proof = new ProofEntity();
        proof.setSecret("sec");
        proof.setUnblindedSignature("sig");
        proof.setMint(new MintEntity());

        ProofConfiguration cfg = new ProofConfiguration(new MintConfiguration("id"), "sec");
        TestProofVault vault = new TestProofVault(cfg, proof);

        String signature = vault.retrieveSignature("sec", false);
        assertThat(signature).isEqualTo("sig");
    }

    @Test
    void retrieveSignatureWrongSecretThrows() {
        ProofEntity proof = new ProofEntity();
        proof.setSecret("sec");
        proof.setUnblindedSignature("sig");
        proof.setMint(new MintEntity());

        ProofConfiguration cfg = new ProofConfiguration(new MintConfiguration("id"), "sec");
        TestProofVault vault = new TestProofVault(cfg, proof);

        assertThatThrownBy(() -> vault.retrieveSignature("wrong", false))
                .isInstanceOf(CashuErrorException.class);
    }
}
