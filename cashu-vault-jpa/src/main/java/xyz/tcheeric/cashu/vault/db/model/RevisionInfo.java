package xyz.tcheeric.cashu.vault.db.model;

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

/**
 * Revision entity for Hibernate Envers auditing.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity
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
}