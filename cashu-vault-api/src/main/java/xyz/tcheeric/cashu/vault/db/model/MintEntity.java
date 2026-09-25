package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entity representing a mint which issues proofs and key sets.
 */
@Entity(name = "mint")
@Table(name = "t_mint")
@Data
@Audited
@AuditTable(value = "t_mint_a")
@EqualsAndHashCode(callSuper = true, exclude = {"proofs", "keySets"})
@ToString(callSuper = true, exclude = {"proofs", "keySets"})
public class MintEntity extends BaseEntity {

    /**
     * Proofs issued by this mint.
     *
     * <p>Not cascading, and read-only in practice. Nothing in the codebase adds to this
     * collection: proofs are written through {@code ProofRepository} with their
     * {@code mint_id} set. The cascade was therefore pure cost, and with
     * {@code ProofEntity.mint} cascading back it meant one proof write hydrated every
     * proof the mint had issued. See {@code ProofEntity.mint} for the measurements.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "mint")
    private Set<ProofEntity> proofs = new LinkedHashSet<>();

    /**
     * Key sets associated with this mint.
     *
     * <p>Not cascading, for the same reason: key sets are written through
     * {@code KeySetRepository}, never by saving a mint. Keeping the cascade here would
     * leave the same write amplification on the keyset path that the proof path had.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "mint")
    private Set<KeySetEntity> keySets = new LinkedHashSet<>();

    /**
     * Returns an unmodifiable view of the proofs to prevent external modification.
     * @return unmodifiable set of proofs
     */
    public Set<ProofEntity> getProofs() {
        return Collections.unmodifiableSet(proofs);
    }

    /**
     * Returns an unmodifiable view of the key sets to prevent external modification.
     * @return unmodifiable set of key sets
     */
    public Set<KeySetEntity> getKeySets() {
        return Collections.unmodifiableSet(keySets);
    }
}
