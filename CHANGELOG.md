# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.8.0] - 2026-05-24

### Added — spec 001 (Append-Only Proof Storage and Mint-Scoped Lookup)

- Spring Security (`spring-boot-starter-security`) — HTTP Basic auth on every `/vault/**` write/admin endpoint and mint-scoped read; `ROLE_ADMIN` / `ROLE_SERVICE` + `MINT:<uuid>` granted authority for scope cross-checks (FR-003, FR-006)
- Admin tombstone endpoint `POST /vault/proof/mint/{mintId}/secret/{secret}/tombstone` — replaces physical `DELETE /vault/proof/{id}` (FR-001, FR-002, FR-003). Tombstone is one-way; re-tombstone returns 409 `ALREADY_TOMBSTONED`. UNSPENT proofs require an explicit `force=true` request-body flag, emitting a high-severity log line
- State-transition endpoint `POST /vault/proof/mint/{mintId}/secret/{secret}/state` — JPA-managed UPDATE that touches only `state` + `updated_at`; identity columns are now immutable post-insert (FR-008)
- Admin audit timeline endpoint `GET /vault/proof/mint/{mintId}/secret/{secret}/audit` — returns full Envers revision list with `principalId` per revision plus current tombstone metadata (FR-013)
- `ProofVaultService` — sole entry point for proof mutations; enforces `insertIfNotExists` with id-collision pre-check + value-mismatch comparison on duplicate (FR-009)
- `RevisionInfo.principalId` — Envers revision rows now carry the Spring Security principal id via a custom `PrincipalRevisionListener` (FR-013 attribution)
- Migration `V4__add_proof_tombstone_columns.sql` — nullable `tombstoned_at`/`tombstoned_by` on `t_proof` and `t_proof_a`; `principal_id` on `revinfo`
- Testcontainers PostgreSQL test base (`PostgresIntegrationTest`) for new write-path ITs (Constitution IV)
- New ITs covering FR-001 through FR-013 acceptance scenarios: `ProofTombstoneIT`, `ProofInsertMismatchIT`, `ProofStateTransitionIT`, `ProofScopeViolationIT`, `ProofAuditTimelineIT`, `ProofRepositoryContractTest`
- README "Security & Network Posture" section (FR-014)

### Changed

- `ProofEntity.secret`, `unblindedSignature`, and `mint` are now `updatable=false` — DB-level backstop for FR-008 ("no silent overwrite"). Service-layer guard (`ProofVaultService.store`) catches the same attempt explicitly with 409 `IDENTITY_CONFLICT`
- `ProofVaultController.store(...)` now delegates to `ProofVaultService.store(...)` (FR-009)
- `DBProofVault.invalidate(...)` and `storePending(...)` route through the new state-transition REST helper instead of re-saving the entity (FR-008)
- `GlobalExceptionHandler` returns the new `ErrorEnvelope` shape for all 4xx/5xx, with explicit mappings for `DataIntegrityViolationException → 409 IDENTITY_CONFLICT`, `AccessDeniedException → 403 SCOPE_VIOLATION` (with the four spec-mandated structured-log fields), `HttpRequestMethodNotSupportedException → 405 METHOD_NOT_ALLOWED`, and `ResponseStatusException → ErrorEnvelope` wrapping

### Removed

- `ProofRepository.findBySecret(String)` — global secret lookup violated Constitution I (FR-005)
- `ProofClient.getBySecret(String)` and `DBProofVault.retrieveProof(String secret)` two-arg overloads
- `DELETE /vault/proof/{id}` — physical deletion is forbidden (FR-001); `DBProofVault.delete(...)` throws `UnsupportedOperationException`
- All `JpaRepository` `delete*` mutators on `ProofRepository` are overridden to throw `UnsupportedOperationException`

### Deprecated

- `GET /vault/proof/secret/{secret}` — returns 400 `MINT_SCOPE_REQUIRED` with a deprecation envelope; removed entirely in v0.8.0

### Security

- Append-only history for proofs enforced at three layers: removed delete endpoint, repository delete-method lockdown, identity-column `updatable=false` at the JPA mapping
- Every rejected mutation (scope violation, identity conflict, tombstone rejection) emits a structured log line with `outcome=<code>`, `principal_id`, and request-scoped fields suitable for SIEM ingestion (FR-011)
- Vault REST API documented as service-only (private network); README adds a Security & Network Posture section (FR-014)

### Notes

- **FR-012 — strict on the live row, approximate on the audit row.** `ProofVaultService.tombstone` now does a JPA setter+save (so Envers fires) **followed by** an immediate native `UPDATE t_proof SET tombstoned_at = now() WHERE id = :id` and an `em.refresh(...)`. The live row's `tombstoned_at` carries the DB clock (asserted by `ProofTombstoneIT.tombstonedAtIsDbClockOnLiveRow` which brackets the call with two `SELECT now()` samples). The audit row's `tombstoned_at` carries the JPA-managed value at flush time — a sub-second JVM-clock approximation of the canonical live value, acceptable since the audit row is forensic evidence and the live row is the authoritative source. State-transition `updated_at` values are already DB-clock via the `updateState` native query.
- **Constitution III (controllers in `cashu-vault-api`) — documented deferral**: controllers remain in `cashu-vault-jpa` per plan.md Complexity Tracking. Tracked for a follow-up consolidation spec.

## [0.7.0] - 2026-02-18

### Added

- New `cashu-vault-hashi` module for HashiCorp Vault secrets backend using `spring-vault-core` 3.2.0
- `HashiVaultClient` with CAS-aware KV v2 operations (store, get, delete)
- `HCKeyVault` and `HCKeySetVault` implementations that store private keys in HashiCorp Vault
- HashiCorp Vault is the default and only secrets backend for private key material
- `HashiVaultConfig` with support for Token, AppRole and Kubernetes authentication
- `HashiVaultRegistrar` for automatic backend registration on startup
- `VaultClientFactory.Backend` enum with `getVault()` method for backend-agnostic vault access
- `VaultClientFactory.registerHCVault()` for pluggable HashiCorp Vault registration
- `vault_path` column on `KeyEntity` for referencing secrets stored in HashiCorp Vault
- V3 Flyway migration: add `vault_path` column, drop `private_key` column
- HashiCorp Vault service and `vault-init` container in Docker Compose
- `docker.env` / `docker.env.example` for externalized configuration
- Integration tests for `HashiVaultClient` using Testcontainers VaultContainer
- `application-hashicorp.properties` default profile for HashiCorp Vault backend

### Changed

- `VaultClientFactory` defaults to HashiCorp Vault backend
- `KeyEntity.privateKey` is now `@Transient` (not persisted to database)
- Docker Compose refactored to use `env_file` instead of inline environment variables
- Docker Compose now includes persistent volumes for PostgreSQL and Vault data

### Security

- Private keys can now be stored in HashiCorp Vault with KV v2 versioning and audit logging
- AppRole least-privilege policy restricts access to `cashu/` mount paths only
- `docker.env` is gitignored to prevent credential leakage

## [0.6.0] - 2026-02-03

### Added

- Production Spring profile (`application-prod.properties`) with PostgreSQL and HikariCP configuration
- G1GC garbage collector configuration in Dockerfile with heap sizing and GC logging
- Bean Validation annotations (`@NotBlank`, `@Pattern`, `@Size`, `@Positive`) on all controller path parameters
- `GlobalExceptionHandler` for centralized exception handling with sanitized error messages

### Changed

- Updated cashu-lib dependency to 0.16.0
- Use `TreeMap` instead of `HashMap` in `DBMintVault.getKeys()` to prevent hash collision DoS
- Return defensive copies (unmodifiable collections) from `MintEntity` and `KeySetEntity` getters
- Initialize collections with known sizes for better performance
- HikariCP connection pool tuning (pool size, timeouts, prepared statement caching)

### Security

- Implement Oracle Secure Coding Guidelines for Java SE (83% compliance)
- Exception message sanitization to prevent information disclosure
- Input validation on all REST endpoints to prevent injection attacks

### Performance

- Implement Java Performance Tuning recommendations (80% compliance)
- Configure G1GC with 200ms pause time target
- Add heap dump on OOM for production diagnostics

## [0.5.0] - 2026-01-26

### Added

- Database unique constraints on `(mint_id, secret)` and `(mint_id, C)` to prevent duplicate proof storage
- SHA-256 fingerprint column for token-level duplicate detection with automatic computation via `@PrePersist`
- `insertIfNotExists()` method in ProofRepository with `InsertResult` record for duplicate handling
- `existsByMint_IdAndSecret()`, `existsByMint_IdAndUnblindedSignature()`, and `findByFingerprint()` repository methods
- Comprehensive integration tests for duplicate detection scenarios

### Changed

- ProofVaultController now returns HTTP 409 Conflict when storing duplicate proofs
- Updated cashu-lib dependency to 0.13.0
- Configured maven-surefire-plugin 3.5.3 for JUnit 5 support

### Security

- Implemented proof uniqueness constraints as part of security hardening (P0-VAULT-001 through P0-VAULT-004)

## [0.4.6] - 2026-01-10

### Changed

- Updated cashu-lib dependency to 0.11.1
- Added comprehensive coding guidelines to AGENTS.md (Clean Code, SOLID principles, Design Patterns)

## [0.4.5] - 2025-12-XX

### Changed

- Version bump release

## [0.4.4] - 2025-12-XX

### Changed

- Version bump release

## [0.4.3] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.7.2

## [0.4.1] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.7.0

## [0.4.0] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.6.2

### Fixed

- Aligned hibernate-envers version with hibernate-core

[Unreleased]: https://github.com/398ja/cashu-vault/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/398ja/cashu-vault/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/398ja/cashu-vault/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/398ja/cashu-vault/compare/v0.4.6...v0.5.0
[0.4.6]: https://github.com/398ja/cashu-vault/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/398ja/cashu-vault/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/398ja/cashu-vault/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/398ja/cashu-vault/compare/v0.4.1...v0.4.3
[0.4.1]: https://github.com/398ja/cashu-vault/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/398ja/cashu-vault/releases/tag/v0.4.0
