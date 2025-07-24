package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "mint")
@Table(name = "t_mint")
@Data
@Audited
@AuditTable(value = "t_mint_a")
@EqualsAndHashCode(callSuper = true, exclude = {"proofs", "keySets"})
@ToString(callSuper = true, exclude = {"proofs", "keySets"})
public class MintEntity extends BaseEntity {

    @JsonIgnore
    @OneToMany(mappedBy = "mint", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProofEntity> proofs = new LinkedHashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "mint", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<KeySetEntity> keySets = new LinkedHashSet<>();
}
