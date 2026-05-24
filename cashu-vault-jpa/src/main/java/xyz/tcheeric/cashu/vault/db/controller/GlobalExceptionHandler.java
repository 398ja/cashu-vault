package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import xyz.tcheeric.cashu.common.util.CashuErrorException;
import xyz.tcheeric.cashu.vault.db.dto.ErrorEnvelope;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized handler for uncaught exceptions thrown by controllers.
 * Every 4xx/5xx response uses the {@link ErrorEnvelope} shape (FR-011) and is
 * accompanied by a structured log line carrying outcome + principal + path.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean-validation failure on @RequestBody — 400 VALIDATION_FAILED.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest req) {
        String first = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("invalid body");
        logStructured("validation_failed", req, Map.of("detail", first));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorEnvelope("VALIDATION_FAILED", first));
    }

    /**
     * Bean-validation failure on path/query params — 400 VALIDATION_FAILED.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest req) {
        logStructured("validation_failed", req, Map.of("detail", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorEnvelope("VALIDATION_FAILED", "invalid request parameters"));
    }

    /**
     * Identity-column conflict from ProofVaultService.store (FR-009).
     * Other DataIntegrityViolationExceptions fall through to a generic 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest req) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.startsWith("identity-column conflict")) {
            String reason = msg.contains("id_collision") || msg.contains("an id that already exists")
                    ? "id_collision"
                    : "value_mismatch";
            logStructured("integrity_mismatch", req, Map.of("reason", reason));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorEnvelope("IDENTITY_CONFLICT", msg, Map.of("reason", reason)));
        }
        logStructured("data_integrity_violation", req, Map.of());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorEnvelope("CONFLICT", "data integrity violation"));
    }

    /**
     * Mint-scope cross-check rejection (FR-006) or admin-only endpoint rejection.
     * Spec US3 AS2 mandates structured log includes (requested_mint_id,
     * lookup_secret_prefix, principal_id, outcome=scope_violation). The path is parsed
     * for the mint UUID + secret prefix on a best-effort basis.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest req) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.toLowerCase().contains("mint-scope") || msg.toLowerCase().contains("mint scope")) {
            Map<String, Object> fields = new HashMap<>(scopeViolationFields(req));
            fields.put("reason", "scope_mismatch");
            logStructured("scope_violation", req, fields);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorEnvelope("SCOPE_VIOLATION",
                            "mint-scope mismatch — principal not authorized for the requested mint"));
        }
        logStructured("access_denied", req, Map.of("detail", msg));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorEnvelope("FORBIDDEN", "access denied"));
    }

    /**
     * Optimistic locking conflict — 409.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex,
                                                                        HttpServletRequest req) {
        logStructured("optimistic_lock_conflict", req, Map.of());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorEnvelope("CONFLICT",
                        "the resource was modified by another request"));
    }

    /**
     * Typed service errors thrown as ResponseStatusException carry a stable code in the reason text
     * (e.g. {@code ALREADY_TOMBSTONED}, {@code TOMBSTONE_REQUIRES_FORCE}). Wrap them in ErrorEnvelope.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorEnvelope> handleResponseStatus(ResponseStatusException ex,
                                                              HttpServletRequest req) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        String code = looksLikeCode(reason) ? reason : "REQUEST_REJECTED";
        logStructured(reason, req, Map.of());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorEnvelope(code, reason));
    }

    private static boolean looksLikeCode(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isUpperCase(c) || c == '_' || Character.isDigit(c))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Domain errors from cashu-lib — 404 (preserves the pre-existing controller contract
     * relied upon by *VaultControllerIntegrationTest classes).
     */
    @ExceptionHandler(CashuErrorException.class)
    public ResponseEntity<ErrorEnvelope> handleCashuErrorException(CashuErrorException ex,
                                                                   HttpServletRequest req) {
        logStructured("cashu_error", req, Map.of("detail", ex.getMessage() != null ? ex.getMessage() : ""));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorEnvelope("CASHU_ERROR", ex.getMessage() != null ? ex.getMessage() : "cashu error"));
    }

    /**
     * Spring raises this when a route exists for a different HTTP verb (e.g. DELETE on a path
     * that only handles GET/POST after FR-001 removed the DELETE endpoint). Must return 405, not 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest req) {
        logStructured("method_not_allowed", req, Map.of("method", ex.getMethod()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorEnvelope("METHOD_NOT_ALLOWED",
                        "method " + ex.getMethod() + " is not allowed for this resource"));
    }

    /**
     * Catch-all. Logs full stack at the server; returns generic 500 with no message leakage.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleException(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception path={} principal={}", req != null ? req.getRequestURI() : "?",
                principalName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorEnvelope("INTERNAL_ERROR",
                        "an internal error occurred"));
    }

    // ---------- helpers ----------

    private void logStructured(String outcome, HttpServletRequest req, Map<String, Object> extra) {
        StringBuilder sb = new StringBuilder("event=rejected_request outcome=").append(outcome);
        if (req != null) {
            sb.append(" path=").append(req.getRequestURI());
            sb.append(" method=").append(req.getMethod());
        }
        sb.append(" principal=").append(principalName());
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        log.warn(sb.toString());
    }

    private static String principalName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null ? a.getName() : "anonymous";
    }

    /**
     * Extracts the four spec-mandated fields for scope-violation logs:
     * requested_mint_id, lookup_secret_prefix, principal_id, outcome.
     * Path may be /vault/proof/mint/{uuid}/secret/{secret}/... — best-effort parse.
     */
    private static Map<String, Object> scopeViolationFields(HttpServletRequest req) {
        Map<String, Object> out = new HashMap<>();
        out.put("principal_id", principalName());
        if (req == null) {
            return out;
        }
        String[] parts = req.getRequestURI().split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("mint".equals(parts[i]) && i + 1 < parts.length) {
                out.put("requested_mint_id", parts[i + 1]);
            }
            if ("secret".equals(parts[i]) && i + 1 < parts.length) {
                String s = parts[i + 1];
                out.put("lookup_secret_prefix", s.length() > 6 ? s.substring(0, 6) : s);
            }
        }
        return out;
    }
}
