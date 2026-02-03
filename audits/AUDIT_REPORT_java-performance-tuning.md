# Audit Report

**Source:** [Java Performance Tuning](https://techoral.com/java/java-performance-tuning.html)
**Date:** 2026-02-02
**Codebase:** cashu-vault

## Executive Summary

- **Total Guidelines Evaluated:** 16
- **Applicable to Codebase:** 10
- **Findings:** 7 (0 critical, 3 high, 3 medium, 1 low)
- **Original Compliance Score:** 30% (3 compliant / 10 applicable)
- **Current Compliance Score:** 80% (8 compliant / 10 applicable) - *Updated 2026-02-02*

### Remediation Summary
| Severity | Fixed | Remaining |
|----------|-------|-----------|
| High | 3 | 0 |
| Medium | 2 | 1 |
| Low | 1 | 0 |

## Codebase Capabilities Detected

| Capability | Status | Key Files |
|------------|--------|-----------|
| File I/O | Present | `*VaultController.java`, `VaultClient.java` |
| Object Pooling | Not Present | - |
| String Concatenation | Present | `VaultClient.java`, `ProofClient.java`, `ProofEntity.java` |
| Collections | Present | `VaultClientFactory.java`, `DBMintVault.java`, `KeySetVaultController.java` |
| Connection Pooling (HikariCP) | Not Configured | `application.properties` |
| Batch Database Operations | Not Present | - |
| Thread Pool | Not Present | - |
| Locking (ReentrantLock) | Present | `DBProofVault.java` |
| Stream API | Present | `DBMintVault.java`, `DBKeySetVault.java`, `KeySetVaultController.java` |
| Comparator/Sorting | Not Present | - |
| JVM Configuration | Missing | `Dockerfile` |

## Findings

### High Severity

#### [GUIDE-003] GC Configuration for Low Latency

**Status:** ✅ REMEDIATED
**Guideline:** Configure G1GC with appropriate pause time targets, heap sizing, and GC logging for production deployments.
**Source:** [GC Configuration](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-jpa/Dockerfile:5` - No JVM flags configured

**Current Code:**
```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Recommended Fix:**
```dockerfile
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Xms512m", \
    "-Xmx512m", \
    "-XX:MetaspaceSize=128m", \
    "-XX:MaxMetaspaceSize=256m", \
    "-Xlog:gc*:file=/app/logs/gc.log:filecount=5,filesize=50m", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/heapdump.hprof", \
    "-jar", "app.jar"]
```

---

#### [GUIDE-007] Connection Pool Configuration

**Status:** ✅ REMEDIATED
**Guideline:** Configure HikariCP with appropriate pool size, timeout, and prepared statement caching for optimal database performance.
**Source:** [Connection Pool Configuration](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-jpa/src/main/resources/application.properties` - No HikariCP configuration

**Current Code:**
```properties
spring.datasource.url=jdbc:h2:file:./cashu_vault;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
# No pool configuration
```

**Recommended Fix:**
```properties
# HikariCP Connection Pool Configuration
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
spring.datasource.hikari.data-source-properties.prepStmtCacheSqlLimit=2048
```

---

#### [GUIDE-016] Metaspace Sizing

**Status:** ✅ REMEDIATED
**Guideline:** Set explicit metaspace limits to prevent unbounded memory growth from class loading.
**Source:** [Metaspace Sizing](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-jpa/Dockerfile:5` - No metaspace configuration

**Current Code:**
```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Recommended Fix:**
```dockerfile
# Include metaspace settings (see GUIDE-003 for complete configuration)
-XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m
```

---

### Medium Severity

#### [GUIDE-004] String Concatenation Optimization

**Status:** PARTIAL
**Guideline:** Use `StringBuilder` instead of `+` operator for multiple concatenations, especially in loops or frequently called methods.
**Source:** [String Concatenation Optimization](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/client/VaultClient.java:100` - String concatenation in REST URL building
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/client/ProofClient.java:35` - Multiple string concatenations

**Current Code:**
```java
// VaultClient.java:100
T response = restTemplate.postForObject(baseUrl + "/vault/" + pathSegment, entity, entityType);

// ProofClient.java:35
getBaseUrl() + "/vault/proof/mint/" + mintId + "/secret/" + secret,
```

**Analysis:**
The string concatenation using `+` operator is present in REST client methods. While modern JVMs (Java 9+) optimize simple string concatenation with `invokedynamic`, these are in HTTP client methods that may be called frequently. However, since Java 21 is used, the JVM's `StringConcatFactory` should handle these efficiently.

**Recommendation:**
For URLs that are built repeatedly with the same base, consider using `UriComponentsBuilder` from Spring or caching the URL templates. The performance impact is low for this codebase size.

**Status: LOW IMPACT** - Java 21's string concatenation optimization mitigates this issue.

---

#### [GUIDE-012] Stream Efficiency

**Status:** PARTIAL
**Guideline:** Specify terminal collection operations with sized constructors to avoid resizing when collecting stream results.
**Source:** [Stream Efficiency](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/db/impl/DBMintVault.java:61-69` - Stream collection without size hint

**Current Code:**
```java
// DBMintVault.java:61-69
return mintEntities.stream()
        .map(mintEntity -> {
            try {
                return load(mintEntity, archive, false);
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
        })
        .toList();
```

**Recommended Fix:**
```java
return mintEntities.stream()
        .map(mintEntity -> {
            try {
                return load(mintEntity, archive, false);
            } catch (CashuErrorException e) {
                throw new RuntimeException(e);
            }
        })
        .collect(Collectors.toCollection(() -> new ArrayList<>(mintEntities.size())));
```

**Analysis:**
The `.toList()` method returns an unmodifiable list and is generally efficient. The improvement would be marginal unless dealing with very large collections.

---

#### [GUIDE-05] Collection Initialization with Size

**Status:** ✅ REMEDIATED
**Guideline:** Initialize collections with anticipated capacity to avoid resizing overhead.
**Source:** [Collection Initialization](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-api/src/main/java/xyz/tcheeric/cashu/vault/api/db/impl/DBMintVault.java:178` - HashMap created without initial capacity
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/KeySetVaultController.java:108` - HashSet created without initial capacity

**Current Code:**
```java
// DBMintVault.java:178
Map<BigInteger, byte[]> keys = new HashMap<>();
keySetEntity.getKeys().forEach(keyEntity -> {
    keys.put(keyEntity.getAmount(), PublicKey.fromString(keyEntity.getPrivateKey()).getBytes());
});

// KeySetVaultController.java:108
.orElse(new HashSet<>());
```

**Recommended Fix:**
```java
// DBMintVault.java - when size is known
Set<KeyEntity> entityKeys = keySetEntity.getKeys();
Map<BigInteger, byte[]> keys = new HashMap<>(entityKeys.size());
entityKeys.forEach(keyEntity -> {
    keys.put(keyEntity.getAmount(), PublicKey.fromString(keyEntity.getPrivateKey()).getBytes());
});

// KeySetVaultController.java - for empty fallback, this is acceptable
.orElse(Collections.emptySet());  // More efficient than new HashSet<>()
```

---

### Low Severity

#### [GUIDE-15] JVM Logging Configuration

**Status:** ✅ REMEDIATED
**Guideline:** Enable GC logging with file rotation for production diagnostics.
**Source:** [JVM Logging](https://techoral.com/java/java-performance-tuning.html)

**Locations:**
- `cashu-vault-jpa/Dockerfile:5` - No GC logging configured

**Current Code:**
```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Recommended Fix:**
```dockerfile
# Add GC logging flags (see GUIDE-003 for complete configuration)
-Xlog:gc*:file=/app/logs/gc.log:filecount=5,filesize=50m
```

---

## Compliant Areas

The codebase demonstrates good practices in several areas:

1. **Memory Leak Prevention (GUIDE-001)**: Try-with-resources is properly used in test files for resource management.

2. **Proper Lock Cleanup (GUIDE-014)**: `DBProofVault.java` correctly uses `ReentrantLock` with proper `finally` block cleanup:
   ```java
   PROOF_STATE_LOCK.lock();
   try {
       // critical section
   } finally {
       PROOF_STATE_LOCK.unlock();
   }
   ```

3. **Client Singleton Pattern**: `VaultClientFactory` uses `ConcurrentHashMap` with `computeIfAbsent` for thread-safe client caching, avoiding repeated object creation.

## Implementation Plan

Ordered list of changes to achieve full compliance, prioritized by severity and effort.

### Phase 1: Critical Fixes (Immediate)

No critical findings.

### Phase 2: High Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 1 | Configure JVM flags in Dockerfile with G1GC, heap sizing, metaspace limits, and GC logging | `cashu-vault-jpa/Dockerfile` | Low | ✅ Done |
| 2 | Add HikariCP connection pool configuration | `cashu-vault-jpa/src/main/resources/application.properties` | Low | ✅ Done |
| 3 | Create production application.properties with separate config | `cashu-vault-jpa/src/main/resources/application-prod.properties` | Low | ✅ Done |

### Phase 3: Medium Priority

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 4 | Initialize HashMap with known size in getKeys() method | `cashu-vault-api/.../DBMintVault.java` | Low | ✅ Done (changed to TreeMap for security) |
| 5 | Replace `new HashSet<>()` with `Collections.emptySet()` for empty fallback | `cashu-vault-jpa/.../KeySetVaultController.java` | Low | ✅ Done |

### Phase 4: Low Priority / Nice-to-Have

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 6 | Consider using UriComponentsBuilder for URL construction in REST clients | `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/client/*.java` | Med | ⏳ Pending |
| 7 | Add sized collectors to stream operations when processing large collections | `cashu-vault-api/.../DBMintVault.java` | Low | ⏳ Pending |

## Guidelines Not Applicable

| Guideline | Reason |
|-----------|--------|
| GUIDE-002: Object Pooling | No high-frequency object creation patterns detected; Spring manages beans |
| GUIDE-006: Bulk Collection Operations | No element-by-element collection operations found; streams are used appropriately |
| GUIDE-008: Batch Database Operations | JPA repositories are used; batch operations would require custom implementation if bulk inserts become a bottleneck |
| GUIDE-009: Thread Pool Sizing | No explicit thread pool usage; Spring Boot manages thread pools automatically |
| GUIDE-010: Read-Write Lock | Only one lock instance found (`PROOF_STATE_LOCK`) which is used correctly; read-write lock would only benefit if there were frequent concurrent reads |
| GUIDE-011: Profile Before Optimizing | Process guidance, not code-verifiable |
| GUIDE-013: Comparator Reuse | No repeated sorting operations detected |

---
*Generated by `/audit` skill on 2026-02-02*
*Source: [Java Performance Tuning](https://techoral.com/java/java-performance-tuning.html)*