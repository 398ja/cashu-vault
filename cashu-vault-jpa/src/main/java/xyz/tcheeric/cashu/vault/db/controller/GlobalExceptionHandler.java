package xyz.tcheeric.cashu.vault.db.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * Handles known Cashu errors by returning a NOT_FOUND status and the error message.
     *
     * @param ex the Cashu-specific error
     * @return response entity containing the error message
     */
    @ExceptionHandler(CashuErrorException.class)
    public ResponseEntity<String> handleCashuErrorException(CashuErrorException ex) {
        log.warn("Cashu error", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    /**
     * Handles any unhandled exception and returns a generic error response.
     *
     * @param ex the exception to handle
     * @return response entity containing the error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }
}
