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

import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "keyset")
@Table(name = "t_keyset", indexes = {
        @Index(name = "idx_keyset_sat_mint_unq", columnList = "sat, mint_id", unique = true)
})
@Data
@Audited
@AuditTable(value = "t_keyset_a")
@EqualsAndHashCode(callSuper = true, exclude = "keys")
@ToString(callSuper = true, exclude = "keys")
public class KeySetEntity extends BaseEntity {

    @JsonIgnore
    @OneToMany(mappedBy = "keySet", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<KeyEntity> keys = new LinkedHashSet<>();

    @JsonProperty
    @Column(name = "key_set_id", nullable = false, unique = true, length = 16)
    private String keySetId;

    @JsonProperty
    @Column(name = "unit", nullable = false, length = 5)
    private String unit;

    @JsonProperty
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "mint_id")
    private MintEntity mint;

}