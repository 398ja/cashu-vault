package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.math.BigInteger;

@Entity(name = "key")
@Table(name = "t_key", indexes = {
        @Index(name = "idx_key_private_key_unq", columnList = "private_key", unique = true)
})
@Data
@Audited
@AuditTable(value = "t_key_a")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KeyEntity extends BaseEntity {

    @JsonProperty
    @Column(name = "amount", nullable = false)
    private BigInteger amount;

    @JsonProperty
    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @JsonProperty
    @ManyToOne
    @JoinColumn(name = "key_set_id")
    private KeySetEntity keySet;

}
