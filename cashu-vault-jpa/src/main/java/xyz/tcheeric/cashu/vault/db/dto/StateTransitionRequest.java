package xyz.tcheeric.cashu.vault.db.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * State-transition request body for {@code POST /vault/proof/mint/{mintId}/secret/{secret}/state}.
 * Only {@code PENDING} and {@code SPENT} are accepted as targets — {@code UNSPENT} is the
 * initial state and cannot be re-entered (FR-008).
 */
public record StateTransitionRequest(
        @NotBlank
        @Pattern(regexp = "PENDING|SPENT", message = "to must be PENDING or SPENT")
        String to
) {
}
