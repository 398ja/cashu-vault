package xyz.tcheeric.cashu.vault.db.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin audit-timeline response for {@code GET /vault/proof/mint/{mintId}/secret/{secret}/audit}
 * (FR-013). Lists Envers revisions in ascending order plus the current row's tombstone metadata.
 */
public record AuditTimelineDto(
        UUID mintId,
        String secretPrefix,
        Current current,
        List<Revision> revisions
) {

    public record Current(String state, Instant tombstonedAt, String tombstonedBy) {
    }

    public record Revision(
            int rev,
            Instant timestamp,
            String principalId,
            String type,
            String state,
            Instant tombstonedAt
    ) {
    }
}
