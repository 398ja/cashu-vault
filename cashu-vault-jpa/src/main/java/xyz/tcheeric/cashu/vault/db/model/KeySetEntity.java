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
        @Index(name = "idx_keyset_unit_mint_unq", columnList = "unit, mint_id", unique = true),
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