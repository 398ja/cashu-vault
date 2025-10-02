package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import xyz.tcheeric.cashu.common.HashToCurveSecret;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.UnCompressedPublicKey;
import xyz.tcheeric.cashu.common.util.SecretUtil;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;

/**
 * Entity representing a spendable proof issued by a mint.
 */
@Entity(name = "proof")
@Table(name = "t_proof", indexes = {
        @Index(name = "idx_proof_mint_id", columnList = "mint_id")
})
@Data
@Audited
@AuditTable(value = "t_proof_a")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProofEntity extends BaseEntity {

    /** Pending state constant. */
    public static final String STATE_PENDING = "PENDING";
    /** Unspent state constant. */
    public static final String STATE_UNSPENT = "UNSPENT";
    /** Spent state constant. */
    public static final String STATE_SPENT = "SPENT";

    /** Mint that issued this proof. */
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "mint_id", nullable = false)
    @JsonProperty
    private MintEntity mint;

    /** Amount represented by the proof. */
    @JsonProperty
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Secret value of the proof. */
    @JsonProperty
    @Column(name = "secret", nullable = false)
    private String secret;

    /** Unblinded signature associated with the proof. */
    @JsonProperty
    @Column(name = "C", nullable = false)
    private String unblindedSignature;

    /** Optional witness identifier. */
    @JsonProperty
    @Column(name = "witness", unique = true)
    private String witness;

    /** Current state of the proof. */
    @JsonProperty
    @Column(name = "state", nullable = false)
    private String state = STATE_UNSPENT;

    public static <T extends Secret> ProofEntity fromProof(Proof<T> proof, MintEntity mintEntity) {
        ProofEntity proofEntity = new ProofEntity();
        proofEntity.setAmount(proof.getAmount());

        String yCoordinate = SecretUtil.toY(proof.getSecret());
        proofEntity.setSecret(yCoordinate);

        if (proof.getWitness() != null) {
            proofEntity.setWitness(proof.getWitness().toString());
        }

        proofEntity.setUnblindedSignature(proof.getUnblindedSignature().toString());
        proofEntity.setMint(mintEntity);
        return proofEntity;
    }
}