package xyz.tcheeric.cashu.vault.db.dto;

import java.time.Instant;
import java.util.UUID;

public record TombstoneResponse(
        UUID mintId,
        String secret,
        Instant tombstonedAt,
        String tombstonedBy
) {
}
