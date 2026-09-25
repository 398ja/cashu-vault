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

    /**
     * Private key value, populated from HashiCorp Vault on retrieval. Not persisted to the
     * database.
     *
     * <p>Excluded from {@code toString()} (audit M-14): Lombok's generated {@code toString}
     * printed the key verbatim once the entity had been enriched from Vault, so any log line or
     * exception message that happened to include the entity leaked the signing key.
     */
    @JsonProperty
    @Transient
    @ToString.Exclude
    private String privateKey;

    /**
     * Public key derived from the signing key, as compressed secp256k1 hex.
     *
     * <p>Persisted, unlike {@link #privateKey}, so that the batch endpoint can answer it and a
     * caller publishing a keyset needs no per-key read (issue #146). Before this field existed,
     * loading one keyset cost one HTTP round trip and one HashiCorp read per key purely to fetch
     * a private key, derive the public key from it, and throw the private key away: 424 key GETs
     * for 58 distinct key ids over six measured swaps on staging.
     *
     * <p>Storing it widens nothing. This value is what the mint already publishes on
     * {@code /v1/keys} under NUT-01, and it is derived rather than secret.
     *
     * <p>Nullable because rows written before the V12 migration carry no derived value.
     * {@code DBKeySetVault.load} falls back to deriving from the private key for those.
     */
    @JsonProperty
    @Column(name = "public_key", length = 66)
    private String publicKey;

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
