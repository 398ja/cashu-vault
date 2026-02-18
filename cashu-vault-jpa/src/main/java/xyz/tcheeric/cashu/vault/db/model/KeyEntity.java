package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.math.BigInteger;

/**
 * Entity representing a single minting key.
 * The private key is stored in HashiCorp Vault and referenced by vault_path.
 */
@Entity(name = "key")
@Table(name = "t_key", indexes = {
        @Index(name = "idx_key_vault_path", columnList = "vault_path")
})
@Data
@Audited
@AuditTable(value = "t_key_a")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class KeyEntity extends BaseEntity {

    /** Amount that this key signs. */
    @JsonProperty
    @Column(name = "amount", nullable = false)
    private BigInteger amount;

    /** Private key value, populated from HashiCorp Vault on retrieval. Not persisted to the database. */
    @JsonProperty
    @Transient
    private String privateKey;

    /** Reference to the secret stored in HashiCorp Vault. */
    @JsonProperty
    @Column(name = "vault_path", nullable = false, length = 512)
    private String vaultPath;

    /** Owning key set. */
    @JsonProperty
    @ManyToOne
    @JoinColumn(name = "key_set_id")
    private KeySetEntity keySet;

}
