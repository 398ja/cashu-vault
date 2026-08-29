package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entity representing a set of keys for a given mint and unit.
 */
@Entity(name = "keyset")
@Table(name = "t_keyset", indexes = {
        // Not unique here: a mint may hold many archived keysets for a unit, and at
        // most one active one. That is a partial constraint, which JPA cannot
        // express, so it lives in the V5 migration as idx_keyset_unit_mint_active_unq.
        @Index(name = "idx_keyset_unit_mint", columnList = "unit, mint_id"),
        @Index(name = "idx_keyset_key_set_mint_unq", columnList = "key_set_id, mint_id", unique = true)
})
@Data
@Audited
@AuditTable(value = "t_keyset_a")
@EqualsAndHashCode(callSuper = true, exclude = "keys")
@ToString(callSuper = true, exclude = "keys")
public class KeySetEntity extends BaseEntity {

    /** Keys contained in this key set. */
    @JsonIgnore
    @OneToMany(mappedBy = "keySet", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<KeyEntity> keys = new LinkedHashSet<>();

    /** External key set identifier. */
    @JsonProperty
    @Column(name = "key_set_id", nullable = false, length = 16)
    private String keySetId;

    /** Monetary unit associated with the key set. */
    @JsonProperty
    @Column(name = "unit", nullable = false, length = 5)
    private String unit;

    /**
     * NUT-02 fee charged per thousand inputs spent from this key set.
     *
     * <p>Zero means the key set charges no fee, which is what every key set does until an
     * operator sets one. It is stored with the key set rather than beside it because under
     * keyset id v2 the fee is an input to the id preimage, so a different fee is a different
     * key set.
     */
    @JsonProperty("input_fee_ppk")
    @Column(name = "input_fee_ppk", nullable = false)
    private int inputFeePpk;

    /** Mint to which this key set belongs. */
    @JsonProperty
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "mint_id")
    private MintEntity mint;

    /**
     * Returns an unmodifiable view of the keys to prevent external modification.
     * @return unmodifiable set of keys
     */
    public Set<KeyEntity> getKeys() {
        return Collections.unmodifiableSet(keys);
    }
}
