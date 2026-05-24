package xyz.tcheeric.cashu.vault.db.dto;

import java.util.Map;

/**
 * Canonical 4xx/5xx response body shape returned by GlobalExceptionHandler.
 */
public record ErrorEnvelope(String code, String message, Map<String, Object> details) {

    public ErrorEnvelope(String code, String message) {
        this(code, message, Map.of());
    }
}
