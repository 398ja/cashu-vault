package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import xyz.tcheeric.cashu.common.util.CashuErrorException;

/**
 * Centralized handler for uncaught exceptions thrown by controllers.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles known Cashu errors.
     *
     * <p>The message goes to the log, not to the caller (audit L-14). These messages are written
     * for operators and quote identifiers, paths and occasionally the values that caused the
     * failure; a caller can act on none of it, and one who is probing rather than integrating
     * learns about the internals for free. The status code is the part of the answer they can
     * use.
     *
     * @param ex the Cashu-specific error
     * @return an empty NOT_FOUND response
     */
    @ExceptionHandler(CashuErrorException.class)
    public ResponseEntity<String> handleCashuErrorException(CashuErrorException ex) {
        log.warn("Cashu error", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Not found");
    }

    /**
     * Handles validation constraint violations.
     * Returns a 400 Bad Request with a generic validation error message.
     *
     * @param ex the constraint violation exception
     * @return response entity containing validation error message
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Validation error", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Invalid request parameters");
    }

    /**
     * Handles optimistic locking failures.
     * Returns a 409 Conflict with a generic message to avoid leaking internal details.
     *
     * @param ex the optimistic locking failure exception
     * @return response entity containing a generic conflict message
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Conflict detected: the resource was modified by another request");
    }

    /**
     * Handles any unhandled exception and returns a generic error response.
     * Does not expose internal exception details to clients for security reasons.
     *
     * @param ex the exception to handle
     * @return response entity containing a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        // Return generic message to avoid leaking internal details
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An internal error occurred. Please contact support.");
    }
}
