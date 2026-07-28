# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Updated cashu-lib to 0.21.0 (NUT-11 P2PK secret validation). Validation is fail-closed:
  a malformed P2PK lock is now rejected at parse time rather than accepted and misbehaving later.

## [0.9.1] - 2026-05-24

### Security

- **Mass-assignment guard on `insert-or-claim`.** The insert path now builds
  a fresh server-side `ProofEntity` (new id / version / timestamps) instead
  of persisting the request-body entity, so a caller-supplied `id` can no
  longer turn `save()` into a merge that overwrites an unrelated proof row.

### Fixed

- **Idempotent re-claim for the same saga.** When a proof is already
  `PENDING` and bound to the *requesting* `meltSagaId`, `insertOrClaimForSaga`
  now counts it as bound (client retry after timeout) instead of reporting
  it unclaimable.
- **Narrowed integrity-violation handling.** Only `uk_proof_mint_secret`
  uniqueness violations are treated as the insert race; NOT NULL / FK /
  `(mint_id, c)` violations now propagate instead of being silently counted
  as "not bound".
- **400 on malformed proofs.** Blank `secret` / null `amount` / blank
  `unblindedSignature` are rejected up front (previously a blank secret
  NPE'd into a 500, and missing fields surfaced as a quiet partial-bind).

## [0.9.0] - 2026-05-24

### Added

- **Atomic insert-or-claim for melt-saga proof holds (cashu-mint spec 005)** —
  closes the highest-priority finding in the 2026-05-24 backend token
  integrity review: the melt saga's `PROOFS_HELD` guarantee was not
  actually enforced before external Lightning payment.
  - `ProofRepository.insertOrClaimForSaga(proofs, meltSagaId, mintId)`:
    per proof, claims an existing `UNSPENT` row (via the existing
    `markPending` CAS) or inserts a fresh `PENDING` row already bound
    to the saga, returning the total count durably bound. The existing
    `uk_proof_mint_secret` unique constraint is the race guard; the
    INSERT path retries the claim once on a `(mint_id, secret)`
    conflict so a concurrent inserter never produces a duplicate row.
  - New endpoint `POST /vault/proof/mint/{mintId}/saga/{meltSagaId}/insert-or-claim`
    accepting Y-normalised `ProofEntity` rows; the mint scope is
    resolved server-side so a malformed body cannot bind into a
    different tenant.
  - `ProofClient.insertOrClaimForSaga` + `DBProofVault.insertOrClaimForSaga`
    pass-throughs.
  - 6 integration tests: fresh insert, claim-existing-UNSPENT (no
    duplicate row), partial bind when one proof is held by another
    saga, canonical-identity regression (two calls leave exactly one
    row), empty-body 400, unknown-mint 400.
  - No schema migration — reuses the V4 `melt_saga_id` column and the
    `uk_proof_mint_secret` constraint.

## [0.8.0] - 2026-05-23

### Added

- **Melt-saga binding for cashu-mint spec 002 (T010 + T011)** —
  durable exclusive-hold semantics for proofs participating in a
  NUT-05 melt saga. The FR-006 contract is "a proof PENDING-held
  by a melt saga is held by exactly one saga, ever".
  - V4 Flyway migration: nullable `t_proof.melt_saga_id VARCHAR(64)`
    column + `ix_proof_melt_saga_id` lookup index +
    `t_proof_a.melt_saga_id` audit shadow.
  - `ProofEntity.meltSagaId` field with lifecycle Javadoc.
  - `ProofRepository.markPending(proofIds, meltSagaId, mintId)` —
    application-level CAS gated on
    `state='UNSPENT' AND melt_saga_id IS NULL`.
  - `ProofRepository.commitSpent(meltSagaId)` — atomic
    PENDING → SPENT + clear binding.
  - `ProofRepository.refundToUnspent(meltSagaId)` — atomic
    PENDING → UNSPENT + clear binding.
  - `ProofRepository.clearMeltSaga(meltSagaId)` — best-effort
    cleanup.
  - `ProofRepository.findByMeltSagaId(meltSagaId)` —
    operator-visible enumeration.
  - REST surface on `ProofVaultController`:
    `POST /vault/proof/mint/{mintId}/saga/{meltSagaId}/mark-pending`
    (body `List<String>` of proof secrets),
    `POST /vault/proof/saga/{meltSagaId}/commit-spent`,
    `POST /vault/proof/saga/{meltSagaId}/refund`. All return the
    affected rowcount.
  - `ProofClient` SDK methods `markPending` / `commitSpent` /
    `refund` over the existing `RestTemplate`.
  - `DBProofVault` static pass-through helpers
    `markPendingForSaga` / `commitSpentForSaga` /
    `refundForSaga` for cashu-mint to consume.

### Fixed

- `ProofVaultController.markPending` guards against null / empty
  `proofSecrets` and returns 400 BAD_REQUEST instead of letting
  Hibernate's IN-clause crash with a 500.

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

[Unreleased]: https://github.com/398ja/cashu-vault/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/398ja/cashu-vault/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/398ja/cashu-vault/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/398ja/cashu-vault/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/398ja/cashu-vault/compare/v0.4.6...v0.5.0
[0.4.6]: https://github.com/398ja/cashu-vault/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/398ja/cashu-vault/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/398ja/cashu-vault/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/398ja/cashu-vault/compare/v0.4.1...v0.4.3
[0.4.1]: https://github.com/398ja/cashu-vault/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/398ja/cashu-vault/releases/tag/v0.4.0
