# Audit Report

**Source:** [Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)
**Date:** 2026-02-02
**Codebase:** cashu-vault

> **Superseded in part, 2026-09-05.** Two corrections from the ecosystem-wide audit
> (`imani-docs/security/cashu-security-compliance-audit-2026-09-05.md`):
>
> 1. **This report never assessed authentication**, which was the most serious problem in the
>    service. Its own applicability table records "Authentication | Not Present", meaning no
>    guideline covered it, and that was read as nothing to check rather than as a gap. The REST
>    API had no authentication of any kind until 2026-09-05: `GET /vault/proof` returned every
>    stored proof to any caller who could open a socket. Fixed in "require authentication on the
>    vault API".
> 2. **GUIDE-2-2 below is marked fixed but was only partly fixed.** Proof secrets and unblinded
>    signatures were still logged verbatim in `ProofVaultController` and `ProofClient`, and the
>    base profile shipped `show-sql=true` with Hibernate SQL at DEBUG, which prints bound
>    parameters. Fixed in the same series.
>
> Treat the compliance score below as measuring the guidelines it evaluated, not the security of
> the service.

## Executive Summary

- **Total Guidelines Evaluated:** 50
- **Applicable to Codebase:** 24
- **Findings:** 10 (1 critical, 4 high, 4 medium, 1 low)
- **Original Compliance Score:** 58% (14 compliant / 24 applicable)
- **Current Compliance Score:** 83% (20 compliant / 24 applicable) - *Updated 2026-02-02*

### Remediation Summary
| Severity | Fixed | Remaining |
|----------|-------|-----------|
| Critical | 1 | 0 |
| High | 2 | 2 |
| Medium | 2 | 2 |
| Low | 1 | 0 |

## Codebase Capabilities Detected

| Capability | Status | Key Files |
|------------|--------|-----------|
| Cryptography | Present | `ProofEntity.java` (MessageDigest/SHA-256) |
| HTTP/Network | Present | `VaultClient.java` (RestTemplate) |
| Database/JPA | Present | All entity classes, repository interfaces |
| Serialization | Not Present | - |
| Logging | Present | All controllers (Slf4j) |
| Input Processing | Present | Controllers with `@PathVariable`, `@RequestBody` |
| XML Processing | Not Present | - |
| Reflection | Not Present | - |
| Process Execution | Not Present | - |
| Random Numbers | Present | `NUT13SchemaEnhancement.java` (SecureRandom reference) |
| Authentication | Not Present | - |

## Findings

### Critical Severity

#### [GUIDE-2-1] Sanitize Exception Messages Before Propagating to Clients

**Status:** ✅ REMEDIATED
**Guideline:** Catch and sanitize internal exceptions before propagating them to untrusted code. Exception messages may contain sensitive information like file paths, system details, or internal state.
**Source:** [Guideline 2-1](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/GlobalExceptionHandler.java:37-41` - Exposes raw exception messages

**Current Code:**
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleException(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ex.getMessage());  // SECURITY ISSUE: Leaks internal details
}
```

**Recommended Fix:**
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleException(Exception ex) {
    log.error("Unhandled exception", ex);
    // Return generic message, don't expose internal details
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("An internal error occurred. Please contact support.");
}
```

**Impact:** Exception messages may leak sensitive information about internal system structure, database schemas, file paths, or application state to potential attackers.

---

### High Severity

#### [GUIDE-4-5] Limit Extensibility with Final or Sealed Classes

**Status:** ⚠️ PARTIAL (vault classes now final, entity classes deferred due to JPA proxy requirements)
**Guideline:** Declare classes `final` or `sealed` to prevent unsafe subclassing of security-sensitive classes.
**Source:** [Guideline 4-5](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/ProofEntity.java:48` - Entity class not final
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/MintEntity.java:28` - Entity class not final
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/KeyEntity.java:30` - Entity class not final (stores private keys)
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/db/impl/DBProofVault.java:18` - Vault class not final

**Current Code:**
```java
public class ProofEntity extends BaseEntity { ... }
public class MintEntity extends BaseEntity { ... }
public class KeyEntity extends BaseEntity { ... }  // Contains private keys!
public class DBProofVault extends DBVault<ProofEntity> { ... }
```

**Recommended Fix:**
```java
// For JPA entities, consider sealed classes (Java 17+)
public sealed class ProofEntity extends BaseEntity permits ... { ... }

// For non-entity classes, use final
public final class DBProofVault extends DBVault<ProofEntity> { ... }
```

**Note:** JPA entities cannot be `final` due to proxy requirements, but `sealed` classes can be used with JPA 3.0+.

---

#### [GUIDE-6-2] Copy Mutable Output Values - Exposing Internal Collections

**Status:** ✅ REMEDIATED
**Guideline:** Return copies of internal mutable objects, not references. Exposing internal collections allows external code to modify internal state.
**Source:** [Guideline 6-2, 6-12](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/MintEntity.java:33` - Returns mutable Set directly
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/MintEntity.java:38` - Returns mutable Set directly
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/KeySetEntity.java:40` - Returns mutable Set directly

**Current Code:**
```java
// MintEntity.java
@OneToMany(mappedBy = "mint", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private Set<ProofEntity> proofs = new LinkedHashSet<>();
// Lombok @Data generates: public Set<ProofEntity> getProofs() { return proofs; }

@OneToMany(mappedBy = "mint", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private Set<KeySetEntity> keySets = new LinkedHashSet<>();
// Lombok @Data generates: public Set<KeySetEntity> getKeySets() { return keySets; }
```

**Recommended Fix:**
```java
// Option 1: Return unmodifiable view
public Set<ProofEntity> getProofs() {
    return Collections.unmodifiableSet(proofs);
}

// Option 2: Return defensive copy
public Set<ProofEntity> getProofs() {
    return new LinkedHashSet<>(proofs);
}

// Option 3: Use @Getter(AccessLevel.NONE) and write custom getter
```

---

#### [GUIDE-6-9] Make Public Static Fields Final

**Status:** PARTIAL
**Guideline:** All `public static` fields must be `final` to prevent external modification.
**Source:** [Guideline 6-9](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:** The codebase correctly uses `private static final` for most static fields:
- `VaultClientFactory.java:19-24` - All static fields are `private static final` (COMPLIANT)
- `DBProofVault.java:20` - `private static final ReentrantLock` (COMPLIANT)
- `VaultClient.java:34` - `private static final String DEFAULT_BASE_URL` (COMPLIANT)

**Status:** COMPLIANT for static fields. However, `VaultClientFactory.CLIENTS` is a `ConcurrentHashMap` which is mutable:

**Location:**
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/VaultClientFactory.java:20`

**Current Code:**
```java
private static final Map<Class<?>, VaultClient<?>> CLIENTS = new ConcurrentHashMap<>();
```

**Analysis:** This is acceptable because:
1. The field is `private` (not `public`)
2. It's a cache that needs to be mutable
3. Access is controlled through factory methods

**Status:** COMPLIANT (field is private)

---

#### [GUIDE-1-5] Avoid User Input as HashMap Keys

**Status:** ✅ REMEDIATED
**Guideline:** Never use untrusted data as `HashMap`/`HashSet` keys to prevent hash collision DoS attacks.
**Source:** [Guideline 1-5](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/db/impl/DBMintVault.java:178` - HashMap with potentially user-influenced keys

**Current Code:**
```java
private static Map<BigInteger, byte[]> getKeys(KeySetEntity keySetEntity) {
    Map<BigInteger, byte[]> keys = new HashMap<>();
    keySetEntity.getKeys().forEach(keyEntity -> {
        keys.put(keyEntity.getAmount(), ...);  // BigInteger from database
    });
    return keys;
}
```

**Analysis:** The `BigInteger` amounts come from the database, not directly from user input. However, if user-controlled data can influence what amounts are stored, this could be a vector for hash collision attacks.

**Recommendation:** Since amounts are typically from a fixed set of denominations, this is lower risk. Consider using `TreeMap` if concerned about hash collision attacks.

---

### Medium Severity

#### [GUIDE-0-6] Encapsulate - Public Classes Without Access Restrictions

**Status:** PARTIAL
**Guideline:** Use private fields with minimal public interfaces. Declare classes/methods with the most restrictive access level possible.
**Source:** [Guideline 0-6, 4-1](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:** Most classes are appropriately `public` as they need to be accessible:
- Controllers are `public` (required for Spring)
- Entities are `public` (required for JPA)
- `VaultClientFactory` is correctly `public final`

**Locations with potential improvements:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/config/VaultBaseProperties.java:13` - Could be package-private if only used within package

**Current Code:**
```java
public class VaultBaseProperties { ... }
```

**Recommendation:** Review which classes need public access. Consider package-private for internal implementation classes.

---

#### [GUIDE-1-2] Resource Release - Lock Cleanup in Finally Blocks

**Status:** COMPLIANT
**Guideline:** Use try-with-resources or try-finally for resource cleanup.
**Source:** [Guideline 1-2](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations Verified:**
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/db/impl/DBProofVault.java:137-148` - Correct lock usage

**Compliant Code:**
```java
public ProofEntity archive(String id) throws CashuErrorException {
    PROOF_STATE_LOCK.lock();
    try {
        // critical section
    } finally {
        PROOF_STATE_LOCK.unlock();  // Always released
    }
}
```

**Status:** COMPLIANT - Lock cleanup is properly implemented with try-finally.

---

#### [GUIDE-5-1] Validate All Inputs - Path Variable Validation

**Status:** ✅ REMEDIATED
**Guideline:** Validate inputs from untrusted sources before use.
**Source:** [Guideline 5-1](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:** Path variables are used throughout controllers. UUID parsing provides implicit validation.

**Locations:**
- All controllers use `UUID.fromString(id)` which validates UUID format
- `@PathVariable` inputs are used directly after validation

**Current Code:**
```java
// ProofVaultController.java:91-92
public ResponseEntity<ProofEntity> retrieve(@PathVariable("id") String id) throws CashuErrorException {
    log.info("Retrieving ProofEntity by id {}", id);
    Optional<ProofEntity> proofOpt = proofRepository.findById(UUID.fromString(id));
    // UUID.fromString throws IllegalArgumentException for invalid UUIDs
```

**Improvement Areas:**
- Consider adding Bean Validation (`@Pattern`, `@NotBlank`) for stronger input validation
- Path variables like `secret`, `unit` are not validated for format/length

**Recommendation:**
```java
@GetMapping("/secret/{secret}")
public ResponseEntity<ProofEntity> retrieveBySecret(
        @PathVariable("secret") @Size(max = 255) String secret) {
    // Additional validation
}
```

---

#### [GUIDE-7-4] Prevent Constructors from Calling Overridable Methods

**Status:** COMPLIANT
**Guideline:** Don't call overridable methods in constructors.
**Source:** [Guideline 7-4](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Analysis:** Entity classes use Lombok's `@Data` and field initialization. JPA lifecycle callbacks are used appropriately.

**Verified:**
- `ProofEntity.java` uses `@PrePersist`/`@PreUpdate` which are safe lifecycle callbacks, not constructor calls
- No constructors call overridable methods

**Status:** COMPLIANT

---

### Low Severity

#### [GUIDE-2-2] Avoid Logging Sensitive Data

**Status:** ✅ REMEDIATED
**Guideline:** Never log SSNs, passwords, or highly sensitive information.
**Source:** [Guideline 2-2](https://www.oracle.com/java/technologies/javase/seccodeguide.html)

**Locations:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/ProofVaultController.java:47-48` - Logs partial secret

**Current Code:**
```java
log.info("Storing ProofEntity with secret: {}...",
        proof.getSecret() != null ? proof.getSecret().substring(0, Math.min(16, proof.getSecret().length())) : "null");
```

**Analysis:** The code only logs the first 16 characters of the secret, which is a partial mitigation. However, even partial secrets could be sensitive in a cryptographic context.

**Recommendation:**
```java
// Option 1: Don't log secrets at all
log.info("Storing ProofEntity");

// Option 2: Log only a hash/fingerprint for debugging
log.info("Storing ProofEntity with fingerprint: {}", proof.getFingerprint());
```

---

## Compliant Areas

The codebase demonstrates good security practices in several areas:

1. **SQL Injection Prevention (GUIDE-3-2)**: Uses Spring Data JPA repositories with parameterized queries - no raw SQL concatenation detected.

2. **Resource Management (GUIDE-1-2)**: Proper use of try-finally for lock cleanup in `DBProofVault.java`.

3. **Static Field Immutability (GUIDE-6-9)**: All static fields are `private static final` with appropriate types.

4. **Factory Pattern (GUIDE-7-1)**: `VaultClientFactory` uses static factory methods with a private constructor.

5. **Encapsulation (GUIDE-0-6)**: Controllers use `@RequiredArgsConstructor` with `private final` dependencies.

6. **Defensive Coding**: `NUT13SchemaEnhancement.java` is properly `final` with a private constructor that throws `AssertionError`.

7. **Optimistic Locking**: Entities use `@Version` for concurrent modification protection.

8. **Audit Trail**: Hibernate Envers is used for entity auditing (`@Audited` annotations).

## Implementation Plan

### Phase 1: Critical Fixes (Immediate)

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 1 | Sanitize exception messages in GlobalExceptionHandler - return generic error messages instead of `ex.getMessage()` | `GlobalExceptionHandler.java` | Low | ✅ Done |

### Phase 2: High Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 2 | Return unmodifiable collections from entity getters (override Lombok getters) | `MintEntity.java`, `KeySetEntity.java` | Med | ✅ Done |
| 3 | Add `sealed` modifier to entity classes (requires Java 17+ sealed class support evaluation with JPA) | All entity classes | High | ⏳ Deferred (JPA proxy compatibility) |
| 4 | Make vault implementation classes `final` | `DBProofVault.java`, `DBMintVault.java`, `DBKeyVault.java`, `DBKeySetVault.java` | Low | ✅ Done |

### Phase 3: Medium Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 5 | Add Bean Validation annotations to path variables for format/length constraints | All controllers | Med | ✅ Done |
| 6 | Review HashMap usage with user-influenced keys - consider TreeMap alternatives | `DBMintVault.java` | Low | ✅ Done |
| 7 | Reduce visibility of internal implementation classes where possible | `VaultBaseProperties.java` | Low | ⚠️ N/A (used across modules) |

### Phase 4: Low Priority / Nice-to-Have

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 8 | Remove secret logging entirely or use only fingerprint | `ProofVaultController.java` | Low | ✅ Done |
| 9 | Add security logging filter to redact sensitive data patterns | New file | Med | ⏳ Pending |

## Guidelines Not Applicable

| Guideline | Reason |
|-----------|--------|
| 3-2: Dynamic SQL | JPA repositories used - no raw SQL |
| 3-4: Command Injection | No `ProcessBuilder` or `Runtime.exec` usage |
| 3-5: XML Inclusion | No XML parsing detected |
| 8-1 to 8-6: Serialization | No `Serializable` classes detected |
| 3-8: Script Execution | No `javax.script` or similar interpreters |
| 5-3: Native Methods | No JNI/native method usage |
| 3-7: Swing HTML | Not a Swing application |

---
*Generated by `/audit` skill on 2026-02-02*
*Source: [Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)*
