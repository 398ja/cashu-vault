package xyz.tcheeric.cashu.vault.db.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Mint that issued this proof. */
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "mint_id", nullable = false)
    @JsonProperty
    private MintEntity mint;

    /** Amount represented by the proof. */
    @JsonProperty
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * Secret value of the proof.
     *
     * <p>Excluded from {@code toString()} (audit M-14). This field together with
     * {@link #unblindedSignature} is spendable ecash, and an entity reaches a log line or an
     * exception message easily: a single {@code log.debug("... {}", proofEntity)} anywhere would
     * publish a spendable token.
     */
    @JsonProperty
    @Column(name = "secret", nullable = false)
    @ToString.Exclude
    private String secret;

    /** Unblinded signature associated with the proof. Excluded from {@code toString()}; see {@link #secret}. */
    @JsonProperty
    @Column(name = "C", nullable = false)
    @ToString.Exclude
    private String unblindedSignature;

    /** Optional witness identifier. Excluded from {@code toString()}; see {@link #secret}. */
    @JsonProperty
    @Column(name = "witness", unique = true)
    @ToString.Exclude
    private String witness;

    /** Current state of the proof. */
    @JsonProperty
    @Column(name = "state", nullable = false)
    private String state = STATE_UNSPENT;

    /**
     * The exclusive hold on this proof while it is PENDING, or null when the row is UNSPENT or
     * SPENT.
     *
     * <p>A melt saga (cashu-mint spec 002, T010 / FR-006) and a swap across its signing step
     * (cashu-mint#400) both bind here. Sharing one binding is deliberate: it is what makes a swap
     * hold block a melt on the same proof and vice versa. {@link #holdKind} says which flow it is,
     * because the two resolve differently.
     *
     * <p>The lifecycle is mint-driven:
     * <ul>
     *   <li>{@code UNSPENT → PENDING} — set to the new hold id.</li>
     *   <li>{@code PENDING → SPENT} or {@code PENDING → UNSPENT} — cleared
     *       to null.</li>
     * </ul>
     */
    @JsonProperty("hold_id")
    @Column(name = "hold_id", length = 64)
    private String holdId;

    /**
     * Which flow holds this proof, when one does.
     *
     * <p>Null exactly when {@link #holdId} is null. It is recorded rather than inferred from the
     * id, because a melt hold and a swap hold resolve in opposite directions and guessing wrong on
     * a swap hold spends the same value twice.
     */
    @JsonProperty("hold_kind")
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_kind", length = 8)
    private HoldKind holdKind;

    /**
     * SHA-256 fingerprint for token-level duplicate detection.
     * Computed from sorted proof secrets + mint URL.
     */
    @JsonProperty
    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

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
