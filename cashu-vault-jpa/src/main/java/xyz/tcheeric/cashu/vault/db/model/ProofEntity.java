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

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_UNSPENT = "UNSPENT";
    public static final String STATE_SPENT = "SPENT";

    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "mint_id", nullable = false)
    @JsonProperty
    private MintEntity mint;

    @JsonProperty
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @JsonProperty
    @Column(name = "secret", nullable = false)
    private String secret;

    @JsonProperty
    @Column(name = "C", nullable = false)
    private String unblindedSignature;

    @JsonProperty
    @Column(name = "witness", unique = true)
    private String witness;

    @JsonProperty
    @Column(name = "state", nullable = false)
    private String state = STATE_UNSPENT;

}