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

/**
 * Entity storing a {@code BlindedMessage} and its corresponding {@code BlindSignature} as
 * described in NUT-09. Each entry references the {@link KeySetEntity} that
 * produced the signature and the amount that was signed.
 */
@Entity(name = "blindsignature")
@Table(name = "t_blind_signature", indexes = {
        @Index(name = "idx_blind_signature_blinded_message_unq", columnList = "blinded_message", unique = true)
})
@Data
@Audited
@AuditTable(value = "t_blind_signature_a")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BlindSignatureEntity extends BaseEntity {

    /** Key set that produced the blind signature. */
    @JsonProperty
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "keyset_id", nullable = false)
    private KeySetEntity keySet;

    /** Amount that was signed. */
    @JsonProperty
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Blinded message provided by the wallet. */
    @JsonProperty
    @Column(name = "blinded_message", nullable = false, unique = true)
    private String blindedMessage;

    /** Blind signature issued by the mint. */
    @JsonProperty
    @Column(name = "blind_signature", nullable = false)
    private String blindSignature;
}
