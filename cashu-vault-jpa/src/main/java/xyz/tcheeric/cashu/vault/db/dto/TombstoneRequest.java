package xyz.tcheeric.cashu.vault.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin tombstone request body (spec 001 / FR-002, FR-003).
 *
 * @param reason operator-supplied justification, 1..512 chars, REQUIRED.
 * @param force  MUST be {@code true} to tombstone an {@code UNSPENT} proof
 *               (edge case in spec US1 §Acceptance Scenario 4).
 */
public record TombstoneRequest(
        @NotBlank @Size(max = 512, message = "reason must be 1..512 chars") String reason,
        boolean force
) {
}
