package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.UnCompressedPublicKey;
import xyz.tcheeric.cashu.common.util.SecretUtil;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Entity representing a spendable proof issued by a mint.
 */
@Entity(name = "proof")
@Table(name = "t_proof",
        indexes = {
                @Index(name = "idx_proof_mint_id", columnList = "mint_id"),
                @Index(name = "idx_proof_commitment", columnList = "c"),
                @Index(name = "idx_proof_fingerprint", columnList = "fingerprint")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_proof_mint_secret", columnNames = {"mint_id", "secret"}),
                @UniqueConstraint(name = "uk_proof_mint_commitment", columnNames = {"mint_id", "c"})
        }
)
@Data
@Audited
@AuditTable(value = "t_proof_a")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProofEntity extends BaseEntity {

    /** Pending state constant. */
    public static final String STATE_PENDING = "PENDING";
    /** Unspent state constant. */
    public static final String STATE_UNSPENT = "UNSPENT";
    /** Spent state constant. */
    public static final String STATE_SPENT = "SPENT";

    /** Mint that issued this proof. FR-008: identity column, immutable post-insert. */
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "mint_id", nullable = false, updatable = false)
    @JsonProperty
    private MintEntity mint;

    /** Amount represented by the proof. */
    @JsonProperty
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Secret value of the proof. FR-008: identity column, immutable post-insert. */
    @JsonProperty
    @Column(name = "secret", nullable = false, updatable = false)
    private String secret;

    /** Unblinded signature associated with the proof. FR-008: identity column, immutable post-insert. */
    @JsonProperty
    @Column(name = "C", nullable = false, updatable = false)
    private String unblindedSignature;

    /** Optional witness identifier. */
    @JsonProperty
    @Column(name = "witness", unique = true)
    private String witness;

    /** Current state of the proof. */
    @JsonProperty
    @Column(name = "state", nullable = false)
    private String state = STATE_UNSPENT;

    /**
     * SHA-256 fingerprint for token-level duplicate detection.
     * Computed from sorted proof secrets + mint URL.
     */
    @JsonProperty
    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    /**
     * Tombstone timestamp. FR-012 (live row, strict): the live {@code t_proof.tombstoned_at}
     * value MUST come from the database via a native UPDATE using {@code now()}. The JPA
     * setter is used only as a dirty marker to fire Hibernate Envers; the value the setter
     * writes is overwritten by an immediate native UPDATE in {@code ProofVaultService.tombstone}.
     * The audit row ({@code t_proof_a.tombstoned_at}) captures the JPA-managed value at flush
     * time — a sub-second JVM-clock approximation of the canonical live value, acceptable
     * since the audit row is forensic evidence of the operation, not the authoritative source.
     */
    @JsonProperty
    @Column(name = "tombstoned_at")
    private Instant tombstonedAt;

    /**
     * Tombstone principal id (from {@code Authentication.getName()}).
     */
    @JsonProperty
    @Column(name = "tombstoned_by", length = 255)
    private String tombstonedBy;

    /**
     * Ensures fingerprint is computed before persisting.
     */
    @PrePersist
    @PreUpdate
    protected void ensureFingerprint() {
        if (this.fingerprint == null && this.secret != null) {
            String mintId = (this.mint != null && this.mint.getId() != null)
                    ? this.mint.getId().toString()
                    : "";
            this.fingerprint = computeFingerprint(this.secret, mintId);
        }
    }

    /**
     * Computes SHA-256 fingerprint from secret and mint identifier.
     *
     * @param secret proof secret
     * @param mintId mint identifier
     * @return hex-encoded SHA-256 hash (64 characters)
     */
    public static String computeFingerprint(String secret, String mintId) {
        try {
            String input = secret + "||" + mintId;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static <T extends Secret> ProofEntity fromProof(Proof<T> proof, MintEntity mintEntity) {
        ProofEntity proofEntity = new ProofEntity();
        proofEntity.setAmount(proof.getAmount());

        String yCoordinate = SecretUtil.toY(proof.getSecret());
        proofEntity.setSecret(yCoordinate);

        if (proof.getWitness() != null) {
            proofEntity.setWitness(proof.getWitness().toString());
        }

        proofEntity.setUnblindedSignature(proof.getUnblindedSignature().toString());
        proofEntity.setMint(mintEntity);

        // Compute fingerprint for duplicate detection
        String mintId = mintEntity.getId() != null ? mintEntity.getId().toString() : "";
        proofEntity.setFingerprint(computeFingerprint(yCoordinate, mintId));

        return proofEntity;
    }
}
