package xyz.tcheeric.cashu.vault.db.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.cashu.vault.db.books.BooksQuery;
import xyz.tcheeric.cashu.vault.db.books.MintBooks;
import xyz.tcheeric.cashu.vault.db.repos.MintRepository;

/**
 * {@code GET /vault/mint/{mintId}/books?until=...}: spent and pending value of a mint for its books
 * reconciler (cashu-vault#156, cashu-mint#515).
 *
 * <p>A {@code GET}, so the read token is enough (see {@code VaultSecurityConfig}). The answer is
 * amounts only. {@code until} is required and must be in the past, so that the spent part of the
 * answer is reproducible: spent rows are final and their {@code spent_at} is set by the database,
 * so asking again for the same {@code until} returns the same numbers however many spends have
 * happened since.
 */
@RestController
@RequestMapping("/vault/mint")
@RequiredArgsConstructor
@Validated
@Slf4j
public class BooksController {

  private final BooksQuery booksQuery;
  private final MintRepository mintRepository;
  private final Clock clock;

  @GetMapping("/{mintId}/books")
  public ResponseEntity<MintBooks> books(
      @PathVariable("mintId")
          @NotBlank
          @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Invalid UUID format")
          String mintId,
      @RequestParam(name = "until", required = false) String until) {
    Instant watermark;
    try {
      watermark = until == null ? null : Instant.parse(until);
    } catch (DateTimeParseException e) {
      watermark = null;
    }
    if (watermark == null || !watermark.isBefore(clock.instant())) {
      log.warn("books rejected mint={} reason=until_missing_invalid_or_not_past", mintId);
      return ResponseEntity.badRequest().build();
    }
    UUID mintUuid = UUID.fromString(mintId);
    if (!mintRepository.existsById(mintUuid)) {
      return ResponseEntity.notFound().build();
    }
    MintBooks books = booksQuery.booksOf(mintUuid, watermark);
    log.info(
        "books mint={} until={} keysets={} unattributed={}",
        mintId,
        watermark,
        books.spent().size(),
        books.unattributedSpent().count());
    return ResponseEntity.ok(books);
  }
}
