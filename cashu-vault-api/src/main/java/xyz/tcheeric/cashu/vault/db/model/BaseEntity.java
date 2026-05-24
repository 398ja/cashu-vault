package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all JPA entities providing common identifier and audit
 * fields.
 */
@Data
@MappedSuperclass
@Audited
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public abstract class BaseEntity {

    /** Unique identifier of the entity. */
    @JsonProperty
    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    /** Flag indicating whether the entity is archived. */
    @JsonProperty
    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    /** Timestamp when the entity was created. */
    @JsonProperty
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Timestamp of the last update. */
    @JsonProperty
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Optimistic locking version field. */
    @JsonProperty
    @Version
    private Integer version = 0;
}
