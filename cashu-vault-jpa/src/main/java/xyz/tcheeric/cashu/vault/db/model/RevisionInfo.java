package xyz.tcheeric.cashu.vault.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import xyz.tcheeric.cashu.vault.db.config.PrincipalRevisionListener;

/**
 * Revision entity for Hibernate Envers auditing.
 * The {@link PrincipalRevisionListener} populates {@code principalId} from the
 * Spring Security {@code Authentication} at revision creation (spec 001 / FR-013).
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(PrincipalRevisionListener.class)
@Data
public class RevisionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(
        name = "revinfo_seq",
        sequenceName = "revinfo_seq",
        allocationSize = 50
    )
    @RevisionNumber
    private Integer rev;

    @RevisionTimestamp
    private Long revtstmp;

    /** Authenticated principal that produced this revision; null for system/migration revisions. */
    @Column(name = "principal_id", length = 255)
    private String principalId;
}
